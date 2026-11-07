package com.community.terms.service;

import com.community.mail.outbox.repository.master.MailOutboxStore;
import com.community.notificationtemplate.catalog.MessageLocales;
import com.community.notificationtemplate.catalog.RequiredMail;
import com.community.site.entity.SiteSettingEntity;
import com.community.site.repository.master.SiteSettingRepository;
import com.community.terms.entity.TermsKind;
import com.community.terms.repository.master.TermsChangeMailRepositoryQDSL;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회원에게 불리한 약관 변경 메일(필수 안내 메일 {@link RequiredMail#MEMBER_TERMS_CHANGE} — 회원이 끌 수 없는 계정·법적 알림)을 <b>커머스 메일 대기열</b>({@code mail_outbox})에
 * 회원마다 한 줄씩 넣는다(docs/features/terms-operator-contract.md). <b>게시 트랜잭션 안에서만</b> 부른다({@code MANDATORY}) — 게시가 롤백되면 메일 줄도 없다.
 *
 * <ul>
 *   <li><b>받는 회원</b>: 지워지지 않았고 탈퇴하지 않았으며 이메일 인증을 마친 회원 전체(정지 회원도 받는다 — 약관은 계정에 걸린다). 보낼 때 발송기가 한 번 더 본다
 *       (그 사이 탈퇴·미인증이면 CANCELED).</li>
 *   <li><b>나눠 보내기</b>: 보낼 때({@code next_attempt_at})를 1분에 {@value #PER_MINUTE}통씩 미뤄 둔다 — 워커(30초 · 한 바퀴 20통 = 1분 40통)의 절반만 쓰므로 주문 확인 같은
 *       다른 대기열 메일이 뒤로 밀리지 않는다. 5만 명이면 약 42시간에 다 나간다(불리한 변경은 시행 30일 전 게시라 충분하다).</li>
 *   <li>같은 종류·버전·회원으로 두 번 넣지 않는다(열쇠 {@code TERMS_CHANGE:<종류>:<버전>:<회원>}).</li>
 *   <li>변수의 글(문서 이름·변경 요지 없음 문구)은 사이트 표시 언어로 만든다(받는 사람마다 언어가 다를 수 있지만 변수는 한 벌이다 — 메일 틀은 받는 사람 언어).</li>
 * </ul>
 */
@Service
public class TermsChangeMails {

    private static final Logger log = LoggerFactory.getLogger(TermsChangeMails.class);

    /** 1분에 넣는 통 수 */
    static final int PER_MINUTE = 20;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MailOutboxStore outbox;
    private final SnowflakeIdGenerator ids;
    private final Messages messages;
    private final TermsChangeMailRepositoryQDSL recipients;
    private final SiteSettingRepository sites;
    private final Clock clock;

    @Autowired
    public TermsChangeMails(MailOutboxStore outbox, SnowflakeIdGenerator ids, Messages messages, TermsChangeMailRepositoryQDSL recipients, SiteSettingRepository sites) {
        this(outbox, ids, messages, recipients, sites, Clock.systemUTC());
    }

    public TermsChangeMails(MailOutboxStore outbox, SnowflakeIdGenerator ids, Messages messages, TermsChangeMailRepositoryQDSL recipients, SiteSettingRepository sites,
                            Clock clock) {
        this.outbox = outbox;
        this.ids = ids;
        this.messages = messages;
        this.recipients = recipients;
        this.sites = sites;
        this.clock = clock;
    }

    /**
     * 받을 회원마다 한 줄씩 넣는다.
     *
     * @return 넣은 줄 수(드라이버가 세지 못하면 0 일 수 있다 — 로그용)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int enqueue(TermsKind kind, int version, LocalDate effectiveDate, String changeNote) {
        List<Long> members = recipients.recipientIds();
        Optional<SiteSettingEntity> site = sites.findById(SiteSettingEntity.ROW_ID);
        Locale locale = siteLocale(site.map(SiteSettingEntity::getDisplayLanguage).orElse(null));
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("termsName", messages.get(new MessageRef("terms.kindName." + kind.name()), locale));
        variables.put("effectiveDate", effectiveDate.toString());
        variables.put("changeNote", changeNote == null || changeNote.isBlank() ? messages.get(new MessageRef("terms.mail.noChangeNote"), locale) : changeNote);
        variables.put("termsLink", siteLink(site.map(SiteSettingEntity::getPublicUrl).orElse(null), kind.publicPath() + "?version=" + version));

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<MailOutboxStore.BulkRow> rows = new ArrayList<>(members.size());
        for (int i = 0; i < members.size(); i++) {
            long userId = members.get(i);
            rows.add(new MailOutboxStore.BulkRow(ids.nextId(), dedupeKey(kind, version, userId), userId, now.plus(Duration.ofMinutes(i / PER_MINUTE))));
        }
        int inserted = outbox.insertBulk(rows, RequiredMail.MEMBER_TERMS_CHANGE.code(), JSON.writeValueAsString(variables), now);
        log.info("약관 변경 메일을 대기열에 넣었다 kind={} version={} members={} inserted={}", kind, version, members.size(), inserted);
        return inserted;
    }

    static String dedupeKey(TermsKind kind, int version, long userId) {
        return "TERMS_CHANGE:" + kind.name() + ":" + version + ":" + userId;
    }

    /** 사이트 표시 언어(설정 행이 없거나 모르는 값이면 한국어) */
    private static Locale siteLocale(String language) {
        String code = MessageLocales.of(language);
        return MessageLocales.locale(code != null ? code : MessageLocales.KO);
    }

    /** 메일 링크 — 사이트 공개 주소를 알면 절대 주소, 모르면 경로(공구 메일과 같다) */
    private static String siteLink(String base, String path) {
        return base == null || base.isBlank() ? path : base.replaceAll("/+$", "") + path;
    }
}

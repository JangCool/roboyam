package com.community.membernotification.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.membernotification.dto.request.MemberEventUpdateRequest;
import com.community.membernotification.dto.response.MemberEventView;
import com.community.membernotification.entity.MemberEvent;
import com.community.membernotification.entity.MemberEventSettingEntity;
import com.community.membernotification.exception.MemberEventNotFoundException;
import com.community.membernotification.exception.MemberEventNotReadyException;
import com.community.membernotification.repository.master.MemberEventSettingRepository;
import com.community.membernotification.repository.master.MemberNotificationStatsRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 이벤트 관리 — 이벤트마다 <b>사용 여부 · 앱 알림 · 이메일 · 회원이 끌 수 있음</b>을 바꾼다. 정의(영역·받는 사람·잠금·준비 중)는 코드({@link MemberEvent})에 고정이다.
 *
 * <ul>
 *   <li><b>검사 순서</b>: 없는 이벤트(404 {@code MEMBER_EVENT_NOT_FOUND}) → 입력 형식(400 {@code fields.enabled|inApp|email|memberCanTurnOff}) → 준비 중(409
 *       {@code MEMBER_EVENT_NOT_READY}) → 코드가 잠근 이벤트를 회원이 끌 수 있게 함(400 {@code VALIDATION} {@code fields.memberCanTurnOff}).</li>
 *   <li>같은 이벤트를 동시에 바꾸는 요청은 행 잠금으로 줄 세운다(줄이 없으면 처음 값으로 만든다). 바뀐 때만 감사 {@code MEMBER_EVENT_UPDATE}(바뀐 칸의 전·후 값).</li>
 *   <li>발행기({@code MemberNotificationPublisher})가 앱 알림·이메일 칸을 바로 본다 — 이메일은 커밋 뒤 회원 알림 메일({@code MemberNotificationEmailSender}).</li>
 *   <li>「최근 30일 발송 수」는 그 기간에 만든 회원 알림 줄 수다(묶인 댓글 알림은 한 줄).</li>
 * </ul>
 * 입구의 권한(조회 {@code notification.read}, 변경 {@code notification.manage} — 운영자 이벤트와 같다)은 컨트롤러가 선언한다.
 */
@Service
public class MemberEventAdminService {

    private static final Logger log = LoggerFactory.getLogger(MemberEventAdminService.class);
    private static final Duration SENT_WINDOW = Duration.ofDays(30);

    private final MemberEventSettingRepository settings;
    private final MemberNotificationStatsRepositoryQDSL stats;
    private final AuditLogService audit;
    private final Messages messages;

    public MemberEventAdminService(MemberEventSettingRepository settings, MemberNotificationStatsRepositoryQDSL stats, AuditLogService audit, Messages messages) {
        this.settings = settings;
        this.stats = stats;
        this.audit = audit;
        this.messages = messages;
    }

    /** 모든 회원 이벤트(준비 중 포함) — 카탈로그 차례 */
    @Transactional(readOnly = true)
    public List<MemberEventView> list() {
        Map<String, MemberEventSettingEntity> rows = new HashMap<>();
        settings.findAll().forEach(row -> rows.put(row.getCode(), row));
        Map<String, Long> sent = stats.countByTypeSince(Instant.now().minus(SENT_WINDOW));
        Locale locale = messages.locale();
        return Arrays.stream(MemberEvent.values()).map(event -> view(event, values(event, rows.get(event.code())), sentOf(event, sent), locale)).toList();
    }

    @Transactional
    public MemberEventView update(AdminActor actor, String code, MemberEventUpdateRequest request) {
        MemberEvent event = MemberEvent.ofCode(code).orElseThrow(MemberEventNotFoundException::new);
        Map<String, MessageRef> invalid = new LinkedHashMap<>();
        if (request == null || request.enabled() == null) {
            invalid.put("enabled", new MessageRef("member.event.required"));
        }
        if (request == null || request.inApp() == null) {
            invalid.put("inApp", new MessageRef("member.event.required"));
        }
        if (request == null || request.email() == null) {
            invalid.put("email", new MessageRef("member.event.required"));
        }
        if (request == null || request.memberCanTurnOff() == null) {
            invalid.put("memberCanTurnOff", new MessageRef("member.event.required"));
        }
        if (!invalid.isEmpty()) {
            throw new ValidationException(invalid);
        }
        if (!event.featureReady()) {
            throw new MemberEventNotReadyException();
        }
        if (event.locked() && request.memberCanTurnOff()) {
            throw ValidationException.of("memberCanTurnOff", new MessageRef("member.event.locked"));
        }

        Instant now = Instant.now();
        MemberEventSettingEntity row = settings.findWithLockByCode(code).orElseGet(() -> { // V58 이 모든 이벤트의 줄을 넣지만, 카탈로그에 새로 더한 이벤트는 처음 값으로 만든다
            MemberEventSettingEntity created = MemberEventSettingEntity.of(event, now);
            settings.persist(created);
            return created;
        });
        Values before = values(event, row);
        Values after = new Values(request.enabled(), request.inApp(), request.email(), !event.locked() && request.memberCanTurnOff());
        Map<String, Object> from = new LinkedHashMap<>(); // 바뀐 칸만 남긴다
        Map<String, Object> to = new LinkedHashMap<>();
        diff("enabled", before.enabled(), after.enabled(), from, to);
        diff("inApp", before.inApp(), after.inApp(), from, to);
        diff("email", before.email(), after.email(), from, to);
        diff("memberCanTurnOff", before.memberCanTurnOff(), after.memberCanTurnOff(), from, to);
        if (!to.isEmpty()) {
            row.change(after.enabled(), after.inApp(), after.email(), after.memberCanTurnOff(), actor.operatorId(), now);
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MEMBER_EVENT_UPDATE, AuditResult.SUCCESS).target("MEMBER_EVENT", code)
                    .with("before", from).with("after", to));
            log.info("회원 이벤트 설정을 바꿨다 event={} 칸={} by operatorId={}", code, to.keySet(), actor.operatorId());
        }
        long sent = sentOf(event, stats.countByTypeSince(now.minus(SENT_WINDOW)));
        return view(event, after, sent, messages.locale());
    }

    private record Values(boolean enabled, boolean inApp, boolean email, boolean memberCanTurnOff) {
    }

    /** 표의 값(없으면 코드의 처음 값). 잠긴 이벤트의 「회원이 끌 수 있음」은 늘 false 로 보인다 */
    private static Values values(MemberEvent event, MemberEventSettingEntity row) {
        if (row == null) {
            return new Values(event.defaultEnabled(), event.defaultInApp(), event.defaultEmail(), event.defaultMemberCanTurnOff());
        }
        return new Values(row.isEnabled(), row.isInApp(), row.isEmail(), !event.locked() && row.isMemberCanTurnOff());
    }

    private static long sentOf(MemberEvent event, Map<String, Long> sent) {
        return event.type() == null ? 0L : sent.getOrDefault(event.type().name(), 0L);
    }

    private static void diff(String field, boolean before, boolean after, Map<String, Object> from, Map<String, Object> to) {
        if (before != after) {
            from.put(field, before);
            to.put(field, after);
        }
    }

    private MemberEventView view(MemberEvent event, Values values, long sent, Locale locale) {
        return new MemberEventView(event.code(), event.area().id(), messages.get(new MessageRef(event.nameKey()), locale),
                messages.get(new MessageRef(event.descriptionKey()), locale), messages.get(new MessageRef(event.recipientsKey()), locale), event.featureReady(),
                values.enabled(), values.inApp(), values.email(), values.memberCanTurnOff(), event.lockReason() == null ? null : event.lockReason().name(),
                event.boardSettingKind(), sent);
    }
}

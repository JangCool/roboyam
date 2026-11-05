package com.community.emoticon.service;

import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.repository.master.EmoticonContractStore;
import com.community.emoticon.repository.master.EmoticonContractStore.Consent;
import com.community.emoticon.repository.master.EmoticonContractStore.ContractVersion;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 로보티콘 상품화 계약(계약 가정 1 · 「상품화 계약 = 약관 종류」 — S2).
 *
 * <ul>
 *   <li><b>문서</b>는 약관 관리의 종류 {@code ROBOTIKON_CONTRACT}(판·시행일·이력·게시 — 운영자가 {@code /settings/terms} 에서). <b>지금 판</b> = 시행일이 사이트 표시 시간대의 오늘
 *       이전인 가장 큰 버전(가입 약관과 같은 규칙). 게시본이 없으면 계약이 없다 — 유료 희망 신청은 {@code 409 EMOTICON_CONTRACT_REQUIRED}.</li>
 *   <li><b>동의</b>는 따로 부르는 API 가 없다 — 유료 희망 심사 요청 본문의 {@code agreeContractVersion} 이 지금 판과 같으면 그 판 동의를 한 줄 남긴다(추가만 — 같은 판에 이미 동의했으면
 *       다시 남기지 않는다). 판이 바뀌면 다음 유료 신청 때 다시 동의한다(목업 {@code mockStudioSubmit}).</li>
 *   <li><b>작가 화면</b>({@code GET /api/me/emoticon-studio/contract}): 본문은 약관의 정화된 HTML 을 평문으로 바꿔 준다 — 제목 = 첫 {@code h2}·{@code h3}(없으면 null → 컨트롤러가 기본 문구),
 *       요약 = 첫 목록({@code ul}·{@code ol})의 항목(없으면 빈 목록). 운영자는 본문 맨 앞에 제목과 요약 목록을 쓰면 된다.</li>
 * </ul>
 */
@Service
public class EmoticonContractService {

    /** 요약 줄 상한 */
    static final int SUMMARY_MAX = 8;

    private static final Pattern HEADING = Pattern.compile("<h[23][^>]*>(.*?)</h[23]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LIST = Pattern.compile("<(ul|ol)[^>]*>(.*?)</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ITEM = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 지금 판 */
    public record Current(int version, LocalDate effectiveDate, String bodyHtml) {
    }

    private final EmoticonContractStore store;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonContractService(EmoticonContractStore store, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 지금 판 — 게시본이 없거나 모두 시행 전이면 비어 있다 */
    public Optional<Current> current() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), store.siteZone());
        return store.current(today).map((ContractVersion v) -> new Current(v.version(), v.effectiveDate(), v.bodyHtml()));
    }

    /** 작가 화면 — 게시된 계약이 없으면 null(응답 {@code data} 가 빠진다) */
    @Transactional(readOnly = true)
    public EmoticonStudioView.Contract creatorView(long userId) {
        Optional<Current> current = current();
        if (current.isEmpty()) {
            return null;
        }
        String html = current.get().bodyHtml();
        Matcher heading = HEADING.matcher(html);
        String title = null;
        String rest = html;
        if (heading.find()) {
            title = text(heading.group(1)).strip();
            rest = html.substring(0, heading.start()) + html.substring(heading.end());
            if (title.isEmpty()) {
                title = null;
            }
        }
        List<String> summary = new ArrayList<>();
        Matcher list = LIST.matcher(rest);
        if (list.find()) {
            Matcher item = ITEM.matcher(list.group(2));
            while (item.find() && summary.size() < SUMMARY_MAX) {
                String line = text(item.group(1)).strip();
                if (!line.isEmpty()) {
                    summary.add(line);
                }
            }
        }
        List<EmoticonStudioView.Agreement> agreements = store.consents(userId).stream().map(c -> new EmoticonStudioView.Agreement(c.version(), c.agreedAt())).toList();
        return new EmoticonStudioView.Contract(current.get().version(), current.get().effectiveDate().toString(), title, List.copyOf(summary), text(rest).strip(), agreements);
    }

    /** 그 작가가 지금 판에 동의했나(지금 판이 없으면 거짓) */
    public boolean agreedCurrent(long userId) {
        return current().map(c -> store.agreed(userId, c.version())).orElse(false);
    }

    /** 작가들 중 지금 판에 동의한 사람(지금 판이 없으면 빈 집합) */
    public java.util.Set<Long> agreedCurrent(Collection<Long> userIds) {
        return current().map(c -> store.agreedUsers(c.version(), userIds)).orElseGet(java.util.Set::of);
    }

    /** 심사 서랍 — 작가의 가장 최근 동의 + 그 판이 지금 판인가. 동의한 적 없으면 null */
    public EmoticonAdminView.ContractConsent consentOf(long userId) {
        Consent last = store.latest(List.of(userId)).get(userId);
        if (last == null) {
            return null;
        }
        Integer currentVersion = current().map(Current::version).orElse(null);
        return new EmoticonAdminView.ContractConsent(last.version(), last.agreedAt(), currentVersion != null && currentVersion == last.version());
    }

    /** 작가들의 가장 최근 동의 */
    public Map<Long, Consent> latest(Collection<Long> userIds) {
        return store.latest(userIds);
    }

    /** 설정 화면의 계약 요약 — 게시본이 없으면 null */
    public EmoticonAdminView.ContractView summary() {
        return current().map(c -> new EmoticonAdminView.ContractView(c.version(), c.effectiveDate().toString(), store.agreedCount(c.version()))).orElse(null);
    }

    /**
     * 유료 판매에 필요한 지금 판 동의를 확인한다(심사 요청 트랜잭션 안 — 남기기는 {@link #record}).
     * 이미 지금 판에 동의했으면 통과(남길 것 없음 — null), 아니면 보낸 판이 지금 판일 때만 통과(남길 판). 그 밖(게시본 없음 · 다른 판 · 보내지 않음)은 {@code 409 EMOTICON_CONTRACT_REQUIRED}.
     *
     * @return 새로 남길 판 — 이미 동의했으면 null
     */
    public Integer requireAgreement(long userId, Integer agreedVersion) {
        Current current = current().orElseThrow(() -> new EmoticonConflictException(EmoticonConflictException.CONTRACT_REQUIRED));
        if (store.agreed(userId, current.version())) {
            return null;
        }
        if (agreedVersion == null || agreedVersion != current.version()) {
            throw new EmoticonConflictException(EmoticonConflictException.CONTRACT_REQUIRED);
        }
        return current.version();
    }

    /** 동의 한 줄을 남긴다(추가만 — 같은 판이 이미 있으면 그대로) */
    public void record(long userId, int version, long packId, Instant now) {
        store.insert(ids.nextId(), userId, version, now, packId);
    }

    /**
     * 정화된 약관 HTML → 평문. 문단·제목·인용·목록 항목 끝은 줄바꿈, 목록 항목 앞은 「• 」, 가로줄은 빈 줄, 나머지 태그는 지우고 글자 참조를 푼다. 빈 줄은 두 개까지.
     */
    static String text(String html) {
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<li[^>]*>", "• ")
                .replaceAll("(?i)</(p|h2|h3|li|blockquote|ul|ol)>", "\n")
                .replaceAll("(?i)<hr\\s*/?>", "\n\n")
                .replaceAll("(?i)<(p|h2|h3|blockquote)[^>]*>", "\n")
                .replaceAll("<[^>]+>", "");
        s = HtmlUtils.htmlUnescape(s).replace(' ', ' ');
        s = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return s;
    }
}

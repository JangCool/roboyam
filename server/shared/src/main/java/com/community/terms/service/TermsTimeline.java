package com.community.terms.service;

import com.community.terms.dto.TermsStatus;
import com.community.terms.dto.TermsVersionMeta;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 한 종류의 게시본을 오늘(사이트 표시 시간대) 기준으로 나눈 것.
 * <ul>
 *   <li><b>지금 약관</b> = 시행일이 오늘 이전(오늘 포함)인 가장 큰 버전.</li>
 *   <li><b>시행 예정</b> = 시행일이 오늘 뒤인 버전. 안내는 그중 가장 이른 것(시행일, 같으면 작은 버전).</li>
 *   <li><b>지난 버전</b> = 그 밖(시행일이 지났지만 더 큰 버전이 시행 중).</li>
 * </ul>
 */
public final class TermsTimeline {

    /** 지금 약관이 바뀐 뒤 변경 안내를 보이는 기간(시행일부터 날 수) — 시행된 지 이 날 수 안(0 ~ 6일째) */
    public static final int RECENT_NOTICE_DAYS = 7;

    private final List<TermsVersionMeta> versions;
    private final LocalDate today;
    private final TermsVersionMeta current;
    private final TermsVersionMeta upcoming;

    private TermsTimeline(List<TermsVersionMeta> versions, LocalDate today) {
        this.versions = versions.stream().sorted(Comparator.comparing(TermsVersionMeta::version).reversed()).toList();
        this.today = today;
        this.current = this.versions.stream().filter(v -> !v.effectiveDate().isAfter(today)).findFirst().orElse(null);
        this.upcoming = this.versions.stream().filter(v -> v.effectiveDate().isAfter(today))
                .min(Comparator.comparing(TermsVersionMeta::effectiveDate).thenComparing(TermsVersionMeta::version)).orElse(null);
    }

    /** @param versions 한 종류의 게시본(차례 무관) */
    public static TermsTimeline of(List<TermsVersionMeta> versions, LocalDate today) {
        return new TermsTimeline(versions, today);
    }

    /** 게시본 — 버전 큰 것 먼저 */
    public List<TermsVersionMeta> versions() {
        return versions;
    }

    public LocalDate today() {
        return today;
    }

    public Optional<TermsVersionMeta> current() {
        return Optional.ofNullable(current);
    }

    public Optional<TermsVersionMeta> upcoming() {
        return Optional.ofNullable(upcoming);
    }

    /** 마지막(가장 큰) 버전 */
    public Optional<TermsVersionMeta> latest() {
        return versions.isEmpty() ? Optional.empty() : Optional.of(versions.getFirst());
    }

    /** 동의 버전 문자열에 들어갈 지금 버전 — 지금 약관이 없으면 0 */
    public int currentVersionOrZero() {
        return current == null ? 0 : current.version();
    }

    public TermsStatus statusOf(int version) {
        if (current != null && current.version() == version) {
            return TermsStatus.CURRENT;
        }
        return versions.stream().filter(v -> v.version() == version).findFirst()
                .map(v -> v.effectiveDate().isAfter(today) ? TermsStatus.UPCOMING : TermsStatus.PAST)
                .orElse(TermsStatus.PAST);
    }

    /**
     * 변경 안내에 보일 버전 — 시행 예정이 있으면 그것(가장 이른 것), 없으면 지금 약관이 2번 이상이고 시행된 지 {@value #RECENT_NOTICE_DAYS}일 안일 때 지금 약관. 그 밖은 비어 있다.
     */
    public Optional<TermsVersionMeta> notice() {
        if (upcoming != null) {
            return Optional.of(upcoming);
        }
        if (current != null && current.version() >= 2 && ChronoUnit.DAYS.between(current.effectiveDate(), today) < RECENT_NOTICE_DAYS) {
            return Optional.of(current);
        }
        return Optional.empty();
    }
}

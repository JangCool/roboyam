package com.community.sanction.entity;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 사이트 제재 단계(docs/features/member-sanction-contract.md 정책 1) — 경고(1회째 rank 1 · 2회째 rank 2) → 정지 3일(3) → 7일(4) → 30일(5) → 영구 정지(6).
 * 값은 표 {@code member_sanctions.kind}(V136 CHECK)와 같고 한 번 정하면 바꾸지 않는다.
 */
public enum SanctionKind {

    WARNING(0, null),
    SUSPEND_3(1, Duration.ofDays(3)),
    SUSPEND_7(2, Duration.ofDays(7)),
    SUSPEND_30(3, Duration.ofDays(30)),
    BAN(4, null);

    /** 단계 차례(WARNING → BAN) */
    public static final List<SanctionKind> LADDER = List.of(values());

    private final int step;
    private final Duration length;

    SanctionKind(int step, Duration length) {
        this.step = step;
        this.length = length;
    }

    /** 사다리 위치 0(경고) ~ 4(영구 정지) */
    public int step() {
        return step;
    }

    /** 기간 정지의 길이 — 경고·영구 정지는 null */
    public Duration length() {
        return length;
    }

    public boolean isWarning() {
        return this == WARNING;
    }

    /** 계정을 막는 제재(기간 정지·영구 정지) */
    public boolean restricts() {
        return this != WARNING;
    }

    public boolean timed() {
        return length != null;
    }

    /** 단계 순위 — 경고는 몇 번째인지({@code warningNo} 2 면 2, 그 밖 1), 그 밖은 3~6 */
    public int rank(Integer warningNo) {
        if (this == WARNING) {
            return warningNo != null && warningNo == 2 ? 2 : 1;
        }
        return step + 2;
    }

    public static Optional<SanctionKind> of(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String code = raw.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(kind -> kind.name().equals(code)).findFirst();
    }
}

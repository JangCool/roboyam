package com.community.adultboard.service;

import com.community.adultboard.entity.AdultBoardAccessEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/** 19금 게시판 핀번호의 정해진 값(adult-board-pin-contract 「정책」 — 사용자 확정 2026-11-18) */
public final class AdultBoardPinPolicy {

    /** 핀 확인 유지 시간 — 1시간 */
    public static final Duration UNLOCK_TTL = Duration.ofHours(1);
    /**
     * {@code IDLE} 의 「보는 동안 연장」을 세션에 다시 쓰는 간격 — 남은 시간이 이만큼 넘게 줄었을 때만 쓴다(요청마다 세션 행을 고치지 않게).
     * 그래서 실제 유지 시간은 마지막으로 본 뒤 55분~1시간이다
     */
    public static final Duration EXTEND_STEP = Duration.ofMinutes(5);
    /** 연속으로 이만큼 틀리면 잠근다 */
    public static final int MAX_FAILURES = 5;
    /** 잠금 시간 — 10분 */
    public static final Duration LOCK_TIME = Duration.ofMinutes(10);

    static final Set<String> RELOCK_MODES = Set.of(AdultBoardAccessEntity.RELOCK_IDLE, AdultBoardAccessEntity.RELOCK_FIXED);

    private static final Pattern PIN = Pattern.compile("[0-9]{6}");

    private AdultBoardPinPolicy() {
    }

    /** 숫자 정확히 6자리인가(ASCII 숫자만) */
    public static boolean isPin(String value) {
        return value != null && PIN.matcher(value).matches();
    }

    /** 잠금이 지금 걸려 있으면 그 끝 시각, 아니면 null */
    public static Instant activeLock(Instant lockedUntil, Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now) ? lockedUntil : null;
    }

    /** 남은 초(올림, 최소 1) */
    public static long secondsUntil(Instant target, Instant now) {
        long millis = Duration.between(now, target).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }
}

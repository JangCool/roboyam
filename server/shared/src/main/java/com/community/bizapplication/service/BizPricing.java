package com.community.bizapplication.service;

import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 금액 규칙(설계 4절 · ★-6) — 계산만 하고 DB 를 모른다.
 *
 * <ul>
 *   <li>일수 = ceil((끝 − 시작) / 24시간), 최소 1. 줄 공급가 = 단가 × 일수(건당은 × 1). 공급가 = 줄 합.</li>
 *   <li>부가세 = floor(공급가 × 10%) — 원 미만 버림. 합계 = 공급가 + 부가세.</li>
 *   <li>진행 중 일할 환불 제안(가정, 일 단위 — user-web {@code UtilBusiness.proratedRefund} 와 같은 식): 총 일수 = 기간의 한국 날짜 수, 쓴 날 = 시작 날짜부터 오늘(한국)까지(오늘 포함),
 *       제안액 = floor(결제액 × (총 − 쓴 날) / 총). 수수료 없음.</li>
 * </ul>
 */
public final class BizPricing {

    private static final Duration DAY = Duration.ofHours(24);

    /** 금액 — 공급가·부가세·합계 */
    public record Amount(long supply, long vat, long total) {
    }

    /** 일할 제안 — 쓴 날·총 일수·제안액 */
    public record Prorate(int usedDays, int totalDays, long proposed) {
    }

    private BizPricing() {
    }

    /** 일수 — 24시간 단위 올림, 최소 1 */
    public static int days(Instant start, Instant end) {
        long minutes = Duration.between(start, end).toMinutes();
        long perDay = DAY.toMinutes();
        long days = (minutes + perDay - 1) / perDay;
        return (int) Math.max(1, days);
    }

    /** 부가세 10%(버림) */
    public static long vat(long supply) {
        return Math.floorDiv(supply, 10);
    }

    public static Amount amountOf(long supply) {
        long vat = vat(supply);
        return new Amount(supply, vat, supply + vat);
    }

    /** 기간의 한국 날짜 수 — [시작, 끝) 의 마지막 날짜는 끝 바로 앞 순간의 날짜 */
    public static int serviceDays(Instant start, Instant end) {
        LocalDate first = ServiceTime.dateOf(start);
        LocalDate last = ServiceTime.dateOf(end.minusNanos(1));
        return (int) Math.max(1, ChronoUnit.DAYS.between(first, last) + 1);
    }

    /**
     * 긴급 내림 되돌리기의 일할 환불(F5 — 가정, 계약 미확정): 중단 일수 = ceil(중단 시간 / 24시간)(중단이 있으면 최소 1, 총 일수까지), 총 일수 = 기간의 한국 날짜 수,
     * 금액 = floor(결제액 × 중단 일수 / 총). 돌려준 값의 쓴 날 = 총 − 중단 일수.
     */
    public static Prorate downtime(long paid, Instant start, Instant end, Duration lost) {
        int total = serviceDays(start, end);
        long minutes = Math.max(0, lost.toMinutes());
        long perDay = DAY.toMinutes();
        int lostDays = (int) Math.min(total, (minutes + perDay - 1) / perDay);
        return new Prorate(total - lostDays, total, Math.floorDiv(paid * lostDays, total));
    }

    /** 진행 중 일할 제안 — 시작 전이면 쓴 날 0, 끝난 뒤면 쓴 날 = 총 일수 */
    public static Prorate prorate(long paid, Instant start, Instant end, Instant now) {
        int total = serviceDays(start, end);
        LocalDate first = ServiceTime.dateOf(start);
        LocalDate today = ServiceTime.dateOf(now);
        long used = now.isBefore(start) ? 0 : ChronoUnit.DAYS.between(first, today) + 1;
        int usedDays = (int) Math.min(total, Math.max(0, used));
        long proposed = Math.floorDiv(paid * (total - usedDays), total);
        return new Prorate(usedDays, total, proposed);
    }
}

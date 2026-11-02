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

    /** 광고주 요청 중단 환불 제안(V127) — 쓴 날·총 일수 · 일할 · 수수료율 · 수수료 · 예상액(= 일할 − 수수료) */
    public record Stop(int usedDays, int totalDays, long prorated, int feeRate, long fee) {

        public long proposed() {
            return prorated - fee;
        }
    }

    /**
     * 대상 금액(biz-pause-contract 규칙 5 — 가정, 법무 확인 전) — 결제액 × (대상 줄 공급가 합 / 모든 줄 공급가 합), 원 미만 버림. 대상 줄이 모든 줄이면 결제액 그대로, 대상 줄이 없거나
     * 공급가 합이 0 이면 0.
     */
    public static long base(long paid, long eligibleSupply, long totalSupply) {
        if (eligibleSupply <= 0 || totalSupply <= 0 || paid <= 0) {
            return 0;
        }
        if (eligibleSupply >= totalSupply) {
            return paid;
        }
        return java.math.BigInteger.valueOf(paid).multiply(java.math.BigInteger.valueOf(eligibleSupply)).divide(java.math.BigInteger.valueOf(totalSupply)).longValueExact();
    }

    /**
     * 중단 환불(규칙 5): 일할 = floor(대상 × 남은 날 / 총 일수)(총 일수 = 기간의 한국 날짜 수, 쓴 날 = 시작 날짜부터 기준 시각의 한국 날짜까지 그날 포함) → 이미 승인한 환불을 넘지 않게
     * {@code min(일할, 결제액 − 이미 환불)} → 수수료 = floor(일할 × 비율 / 100) → 예상액 = 일할 − 수수료.
     *
     * @param paid           돌려줄 수 있는 결제액(온라인 결제액 · 수동 입금 확인 금액)
     * @param base           대상 금액({@link #base})
     * @param alreadyRefunded 이미 승인한 환불 합(되돌리기 보상 DOWNTIME 등)
     * @param feeRate        그 신청 건에 고정된 중도 해지 수수료율(%)
     */
    public static Stop stop(long paid, long base, Instant start, Instant end, Instant basis, long alreadyRefunded, int feeRate) {
        Prorate p = prorate(base, start, end, basis);
        long prorated = Math.max(0, Math.min(p.proposed(), paid - alreadyRefunded));
        long fee = Math.floorDiv(prorated * feeRate, 100);
        return new Stop(p.usedDays(), p.totalDays(), prorated, feeRate, fee);
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

package com.community.dashboard.dto;

import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 매출 요약({@code SALES_SUMMARY})의 네 기간 — 날짜 경계는 서비스 날짜(한국 시간 0시, commerce.md 3절 「시각은 UTC 저장, 날짜 판단만 KST」). 모두 {@code [from, to)}.
 *
 * <ul>
 *   <li>오늘 = 오늘 0시 ~ 내일 0시 · 어제 = 어제 0시 ~ 오늘 0시.</li>
 *   <li>이번 달 = 이번 달 1일 0시 ~ 내일 0시(오늘까지).</li>
 *   <li>지난달 같은 기간 = 지난달 1일 0시부터 「이번 달 1일 0시 → 지금」 만큼(같은 일·같은 시각까지). 지난달이 더 짧아 넘치면 지난달 끝(이번 달 1일 0시)에서 멈춘다
 *       (예: 3월 30일 10시 → 2월 전체, 3월 15일 10시 → 2월 15일 10시까지).</li>
 * </ul>
 */
public record SalesPeriods(Range today, Range yesterday, Range month, Range lastMonth) {

    /** 시각 범위 {@code [from, to)} */
    public record Range(Instant from, Instant to) {

        public boolean contains(Instant at) {
            return !at.isBefore(from) && at.isBefore(to);
        }
    }

    public static SalesPeriods of(Instant now) {
        LocalDate today = ServiceTime.dateOf(now);
        Instant todayStart = DashboardGadgetQuery.startOf(today);
        Instant tomorrowStart = DashboardGadgetQuery.startOf(today.plusDays(1));
        Instant yesterdayStart = DashboardGadgetQuery.startOf(today.minusDays(1));
        LocalDate first = today.withDayOfMonth(1);
        Instant monthStart = DashboardGadgetQuery.startOf(first);
        Instant lastMonthStart = DashboardGadgetQuery.startOf(first.minusMonths(1));
        Instant lastMonthEnd = lastMonthStart.plus(Duration.between(monthStart, now));
        if (lastMonthEnd.isAfter(monthStart)) {
            lastMonthEnd = monthStart;
        }
        return new SalesPeriods(new Range(todayStart, tomorrowStart), new Range(yesterdayStart, todayStart), new Range(monthStart, tomorrowStart),
                new Range(lastMonthStart, lastMonthEnd));
    }
}

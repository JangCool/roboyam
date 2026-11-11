package com.community.analytics.service;

import com.community.analytics.dto.VisitAnalyticsAdmin.UniqueCompare;
import com.community.analytics.dto.VisitAnalyticsAdmin.UniquePeriod;
import com.community.analytics.dto.VisitAnalyticsAdmin.UniquesAudience;
import com.community.analytics.repository.master.VisitAnalyticsStore;
import com.community.analytics.store.VisitPeriods;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주·월 순방문자 계산(site-analytics-contract 「3단계」 결정 ④) — 원천은 숫자 표 {@code analytics_unique_counts}(analytics V8)뿐이다(표시 표는 읽지 않는다).
 * 「방문자 통계 › 개요」 순방문자 카드({@code GET …/visits/uniques}) · 「월별 보기」의 월 순방문자 · 대시보드 「방문 현황」 이번 주·이번 달이 같은 규칙을 쓴다.
 *
 * <ul>
 *   <li>기간 순방문자 = 그 기간 줄의 합. 「같은 시점까지」 = 지난 기간을 이번 기간이 지난 날 수만큼만(지난달이 짧으면 말일까지) 더한 합.</li>
 *   <li><b>null(집계 전)</b>: 그 기간 줄이 없고, 기간의 끝날이 대상의 세기 시작일(숫자 표의 가장 이른 날)보다 앞이거나 세기 시작일이 없을 때. 세기 시작 뒤의 빈 기간은 0
 *       (수집을 끈 동안 등).</li>
 *   <li>대상: {@link VisitPeriods#VISITOR} · {@link VisitPeriods#MEMBER}. 기간: 주 = 월요일 시작, 달 = 1일(한국 날짜).</li>
 * </ul>
 * 결과 캐시는 부르는 쪽이 둔다(관리자 API 60초 · 대시보드 가젯 캐시).
 */
public final class VisitUniqueCounts {

    static final int PERIODS = 12;
    static final List<String> AUDIENCES = List.of(VisitPeriods.VISITOR, VisitPeriods.MEMBER);

    private final VisitAnalyticsStore store;

    public VisitUniqueCounts(VisitAnalyticsStore store) {
        this.store = store;
    }

    /** 이번 주·이번 달 순방문자(한 대상) — 대시보드 「방문 현황」 한 줄 */
    public record Current(Long week, Long month) {
    }

    /** 대상 하나의 이번 주·이번 달(집계 전이면 null) */
    public Current current(String audience, LocalDate today) {
        LocalDate week = VisitPeriods.weekStart(today);
        LocalDate month = VisitPeriods.monthStart(today);
        LocalDate first = store.uniqueFirstDays().get(audience);
        Long w = total(store.uniqueTotals(VisitPeriods.WEEK, week, week), audience, week);
        Long m = total(store.uniqueTotals(VisitPeriods.MONTH, month, month), audience, month);
        return new Current(count(w, week.plusDays(6), first), count(m, month.plusMonths(1).minusDays(1), first));
    }

    /**
     * 두 대상 모두의 최근 12주·12개월과 이번/지난 기간 비교 — {@code read} 가 false 면(수집 꺼짐) 숫자 표를 읽지 않고 모든 수를 null 로.
     */
    public Map<String, UniquesAudience> audiences(LocalDate today, boolean read) {
        LocalDate thisWeek = VisitPeriods.weekStart(today);
        LocalDate thisMonth = VisitPeriods.monthStart(today);
        LocalDate firstWeek = thisWeek.minusWeeks(PERIODS - 1);
        LocalDate firstMonth = thisMonth.minusMonths(PERIODS - 1);
        LocalDate prevWeek = thisWeek.minusWeeks(1);
        LocalDate prevMonth = thisMonth.minusMonths(1);
        // 지난 기간의 같은 시점: 주 = 지난 주 + (오늘 - 이번 주 월요일), 달 = 지난달 같은 날(말일을 넘지 않게)
        LocalDate weekSame = prevWeek.plusDays(today.toEpochDay() - thisWeek.toEpochDay());
        LocalDate monthSame = prevMonth.plusDays(Math.min(today.getDayOfMonth(), prevMonth.lengthOfMonth()) - 1L);

        Map<String, LocalDate> first = read ? store.uniqueFirstDays() : Map.of();
        Map<String, Map<LocalDate, Long>> weeks = read ? byAudience(store.uniqueTotals(VisitPeriods.WEEK, firstWeek, thisWeek)) : Map.of();
        Map<String, Map<LocalDate, Long>> months = read ? byAudience(store.uniqueTotals(VisitPeriods.MONTH, firstMonth, thisMonth)) : Map.of();
        Map<String, Long> weekUntil = read ? store.uniqueUntil(VisitPeriods.WEEK, prevWeek, weekSame) : Map.of();
        Map<String, Long> monthUntil = read ? store.uniqueUntil(VisitPeriods.MONTH, prevMonth, monthSame) : Map.of();

        Map<String, UniquesAudience> out = new LinkedHashMap<>();
        for (String audience : AUDIENCES) {
            LocalDate f = first.get(audience);
            Map<LocalDate, Long> w = weeks.getOrDefault(audience, Map.of());
            Map<LocalDate, Long> m = months.getOrDefault(audience, Map.of());
            List<UniquePeriod> weekList = new ArrayList<>(PERIODS);
            for (int i = 0; i < PERIODS; i++) {
                LocalDate start = firstWeek.plusWeeks(i);
                LocalDate end = start.plusDays(6);
                weekList.add(new UniquePeriod(start.toString(), end.toString(), read ? count(w.get(start), end, f) : null, start.equals(thisWeek)));
            }
            List<UniquePeriod> monthList = new ArrayList<>(PERIODS);
            for (int i = 0; i < PERIODS; i++) {
                LocalDate start = firstMonth.plusMonths(i);
                LocalDate end = start.plusMonths(1).minusDays(1);
                monthList.add(new UniquePeriod(start.toString(), end.toString(), read ? count(m.get(start), end, f) : null, start.equals(thisMonth)));
            }
            out.put(audience, new UniquesAudience(compare(weekList, weekUntil.get(audience)), compare(monthList, monthUntil.get(audience)), weekList, monthList));
        }
        return out;
    }

    /**
     * 달 여럿의 월 순방문자(한 대상) — 월별 보기. 키 = 그 달 1일, 값 null = 집계 전. 수집 스위치와 무관하게 쌓인 숫자를 읽는다(사용자 결정 ⓐ).
     */
    public Map<LocalDate, Long> months(String audience, LocalDate fromMonth, LocalDate toMonth, Map<String, LocalDate> firstDays) {
        Map<LocalDate, Long> totals = byAudience(store.uniqueTotals(VisitPeriods.MONTH, fromMonth, toMonth)).getOrDefault(audience, Map.of());
        LocalDate f = firstDays.get(audience);
        Map<LocalDate, Long> out = new LinkedHashMap<>();
        for (LocalDate m = fromMonth; !m.isAfter(toMonth); m = m.plusMonths(1)) {
            out.put(m, count(totals.get(m), m.plusMonths(1).minusDays(1), f));
        }
        return out;
    }

    /** 세기 시작일 — 월별 보기가 대상 둘에 한 번만 읽게 */
    public Map<String, LocalDate> firstDays() {
        return store.uniqueFirstDays();
    }

    private static UniqueCompare compare(List<UniquePeriod> list, Long until) {
        UniquePeriod current = list.get(list.size() - 1);
        UniquePeriod previous = list.get(list.size() - 2);
        Long same = previous.count() == null ? null : until == null ? 0L : until;
        return new UniqueCompare(current, previous, same);
    }

    /** 그 기간 합 — 줄이 있으면 그 합, 없으면 세기 시작 전(끝날이 시작일보다 앞이거나 시작일 없음)이면 null, 아니면 0 */
    private static Long count(Long total, LocalDate end, LocalDate first) {
        if (total != null) {
            return total;
        }
        return first == null || end.isBefore(first) ? null : 0L;
    }

    private static Long total(List<VisitAnalyticsStore.PeriodTotal> rows, String audience, LocalDate start) {
        return rows.stream().filter(r -> r.audience().equals(audience) && r.start().equals(start)).map(VisitAnalyticsStore.PeriodTotal::total).findFirst().orElse(null);
    }

    private static Map<String, Map<LocalDate, Long>> byAudience(List<VisitAnalyticsStore.PeriodTotal> rows) {
        Map<String, Map<LocalDate, Long>> out = new HashMap<>();
        rows.forEach(r -> out.computeIfAbsent(r.audience(), k -> new HashMap<>()).put(r.start(), r.total()));
        return out;
    }
}

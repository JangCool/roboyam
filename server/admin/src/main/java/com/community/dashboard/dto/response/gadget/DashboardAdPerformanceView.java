package com.community.dashboard.dto.response.gadget;

import java.time.LocalDate;
import java.util.List;

/** {@code AD_PERFORMANCE} — 어제·7일 합계 + 7일 일별(오래된 차례, 오늘 제외) — 광고 일별 집계({@code ad_daily_stats}) 그대로, 모든 건·자리 합 */
public record DashboardAdPerformanceView(Counts yesterday, Counts week, List<Day> days) implements DashboardGadgetData {

    public record Counts(long impressions, long clicks) {
    }

    public record Day(LocalDate date, long impressions, long clicks) {
    }
}

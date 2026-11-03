package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardAdPerformanceView;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 광고 성과 요약 — 일별 집계 표({@code ad_daily_stats})의 어제 · 7일(오늘 제외) 합과 일별(빈 날은 0) */
@Component
public class AdPerformanceGadget implements DashboardGadgetProvider {

    static final int DAYS = 7;

    private final DashboardGadgetStore store;

    public AdPerformanceGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.AD_PERFORMANCE;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        LocalDate yesterday = query.today().minusDays(1);
        LocalDate from = yesterday.minusDays(DAYS - 1L);
        Map<LocalDate, DashboardGadgetStore.AdDay> byDay = store.adDays(from, yesterday).stream()
                .collect(Collectors.toMap(DashboardGadgetStore.AdDay::day, Function.identity()));
        List<DashboardAdPerformanceView.Day> days = new ArrayList<>();
        long impressions = 0;
        long clicks = 0;
        for (LocalDate d = from; !d.isAfter(yesterday); d = d.plusDays(1)) {
            DashboardGadgetStore.AdDay row = byDay.get(d);
            long imp = row == null ? 0 : row.impressions();
            long clk = row == null ? 0 : row.clicks();
            days.add(new DashboardAdPerformanceView.Day(d, imp, clk));
            impressions += imp;
            clicks += clk;
        }
        DashboardAdPerformanceView.Day last = days.get(days.size() - 1);
        return new DashboardAdPerformanceView(new DashboardAdPerformanceView.Counts(last.impressions(), last.clicks()),
                new DashboardAdPerformanceView.Counts(impressions, clicks), days);
    }
}

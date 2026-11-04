package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardSignupTrendView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 가입 추이(member.read · 수집 항목 {@code boardActivity} — 같은 크론이 센다) — 날 집계({@code site_activity_daily}) 7|30줄 한 번. 가입 = 그 날 만든 회원 수(회원 현황 「오늘 가입」과 같은 기준).
 */
@Component
public class SignupTrendGadget implements DashboardGadgetProvider {

    private final DashboardAnalyticsStore store;

    public SignupTrendGadget(DashboardAnalyticsStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.SIGNUP_TREND;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        LocalDate today = query.today();
        LocalDate from = today.minusDays(query.days() - 1L);
        Map<LocalDate, DashboardAnalyticsStore.SiteDay> byDay = store.siteDays(from, today).stream()
                .collect(Collectors.toMap(DashboardAnalyticsStore.SiteDay::day, Function.identity()));
        List<DashboardSignupTrendView.Day> days = new ArrayList<>(query.days());
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            DashboardAnalyticsStore.SiteDay row = byDay.get(d);
            days.add(new DashboardSignupTrendView.Day(d, row == null ? 0 : row.signups()));
        }
        return new DashboardSignupTrendView(days);
    }
}

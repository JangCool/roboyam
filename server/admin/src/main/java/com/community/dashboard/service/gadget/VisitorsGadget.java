package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardVisitorsView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 방문 현황(site.read · 수집 항목 {@code visits}) — 시간 칸 집계({@code analytics_visit_hourly}) 어제 0시 ~ 지금의 줄(최대 48줄) 한 번 + 지금 접속 COUNT 한 번. 원본을 세지 않는다.
 * 하루 방문자 = 그 날 시간 칸 방문자의 합(시간 칸 방문자는 그 날 처음 본 방문자라 중복이 없다). 「어제 같은 시각까지」는 어제 0시부터 지금 시각이 든 시간 칸까지(칸 단위).
 */
@Component
public class VisitorsGadget implements DashboardGadgetProvider {

    /** 「지금 접속」 창 */
    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(5);

    private final DashboardAnalyticsStore store;

    public VisitorsGadget(DashboardAnalyticsStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.VISITORS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        Instant todayStart = DashboardGadgetQuery.startOf(query.today());
        Instant yesterdayStart = DashboardGadgetQuery.startOf(query.today().minusDays(1));
        Instant nowHour = query.now().truncatedTo(ChronoUnit.HOURS);
        Instant sameHourYesterday = nowHour.minus(Duration.ofDays(1));
        long visitors = 0;
        long newVisitors = 0;
        long views = 0;
        long visitorsYesterday = 0;
        long viewsYesterday = 0;
        Map<Integer, Long> byHour = new HashMap<>();
        for (DashboardAnalyticsStore.Hour h : store.hours(yesterdayStart, nowHour.plus(Duration.ofHours(1)))) {
            if (!h.hour().isBefore(todayStart)) {
                visitors += h.visitors();
                newVisitors += h.newVisitors();
                views += h.pageViews();
                byHour.merge(h.hour().atZone(ServiceTime.ZONE).getHour(), h.visitors(), Long::sum);
            } else if (!h.hour().isAfter(sameHourYesterday)) {
                visitorsYesterday += h.visitors();
                viewsYesterday += h.pageViews();
            }
        }
        int current = query.now().atZone(ServiceTime.ZONE).getHour();
        List<DashboardVisitorsView.Hour> hours = new ArrayList<>(current + 1);
        for (int hour = 0; hour <= current; hour++) {
            hours.add(new DashboardVisitorsView.Hour(hour, byHour.getOrDefault(hour, 0L)));
        }
        long active = store.activeSince(query.now().minus(ACTIVE_WINDOW));
        return new DashboardVisitorsView(visitors, newVisitors, views, active, visitorsYesterday, viewsYesterday, hours);
    }
}

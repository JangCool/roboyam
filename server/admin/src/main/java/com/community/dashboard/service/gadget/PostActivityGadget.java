package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardPostActivityView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 글·댓글 활동(post.read · 수집 항목 {@code boardActivity}) — 게시판 활동 날 집계({@code site_activity_daily}, R__batch_38 이 10분마다 오늘·어제를 다시 센다) 7줄 한 번.
 * 원본(글·댓글 표)을 세지 않는다. 「글」 기준은 R__batch_38 머리말(공개 게시판 · 게시 글 · 지워지지 않은 글, 쓴 날).
 */
@Component
public class PostActivityGadget implements DashboardGadgetProvider {

    static final int DAYS = 7;

    private final DashboardAnalyticsStore store;

    public PostActivityGadget(DashboardAnalyticsStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.POST_ACTIVITY;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        LocalDate today = query.today();
        LocalDate from = today.minusDays(DAYS - 1L);
        Map<LocalDate, DashboardAnalyticsStore.SiteDay> byDay = store.siteDays(from, today).stream()
                .collect(Collectors.toMap(DashboardAnalyticsStore.SiteDay::day, Function.identity()));
        List<DashboardPostActivityView.Day> days = new ArrayList<>(DAYS);
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            DashboardAnalyticsStore.SiteDay row = byDay.get(d);
            days.add(new DashboardPostActivityView.Day(d, row == null ? 0 : row.posts(), row == null ? 0 : row.comments()));
        }
        DashboardPostActivityView.Day t = days.get(days.size() - 1);
        DashboardPostActivityView.Day y = days.get(days.size() - 2);
        return new DashboardPostActivityView(t.posts(), y.posts(), t.comments(), y.comments(), days);
    }
}

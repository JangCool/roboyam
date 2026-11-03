package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardAdScheduleView;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** 이번 주 광고 일정 — 지금부터 7일 안에 시작·종료하는 예약된 광고 건(시각 차례, 최대 8) */
@Component
public class AdScheduleGadget implements DashboardGadgetProvider {

    static final int MAX = 8;
    static final Duration WINDOW = Duration.ofDays(7);

    private final DashboardGadgetStore store;

    public AdScheduleGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.AD_SCHEDULE;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        Instant from = query.now();
        Instant to = from.plus(WINDOW);
        List<DashboardAdScheduleView.Item> items = new ArrayList<>();
        for (DashboardGadgetStore.AdBooking b : store.adBookings(from, to)) {
            if (within(b.startAt(), from, to)) {
                items.add(new DashboardAdScheduleView.Item(b.id() + "-START", b.title(), b.product(), "START", b.startAt()));
            }
            if (within(b.endAt(), from, to)) {
                items.add(new DashboardAdScheduleView.Item(b.id() + "-END", b.title(), b.product(), "END", b.endAt()));
            }
        }
        return new DashboardAdScheduleView(items.stream().sorted(Comparator.comparing(DashboardAdScheduleView.Item::at).thenComparing(DashboardAdScheduleView.Item::id))
                .limit(MAX).toList());
    }

    static boolean within(Instant at, Instant from, Instant to) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }
}

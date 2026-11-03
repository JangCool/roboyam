package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardPointSummaryView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 포인트 요약 — 오늘(한국 날짜) 원장의 지급·사용 합(만든 시각 인덱스 범위 — 하루치) · 7일 안에 만료될 무료 포인트(만료 부분 인덱스).
 * 환불 승인 대기는 승인 기능(point.refund.approve — 기능 준비 중)이 없어 null.
 */
@Component
public class PointSummaryGadget implements DashboardGadgetProvider {

    static final Duration EXPIRY_WINDOW = Duration.ofDays(7);

    private final DashboardGadgetStore store;

    public PointSummaryGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.POINT_SUMMARY;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        Instant todayStart = DashboardGadgetQuery.startOf(query.today());
        long[] flow = store.pointFlow(todayStart, DashboardGadgetQuery.startOf(query.today().plusDays(1)));
        return new DashboardPointSummaryView(flow[0], flow[1], store.pointsExpiring(query.now(), query.now().plus(EXPIRY_WINDOW)), null);
    }
}

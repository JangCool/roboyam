package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardReportsView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import org.springframework.stereotype.Component;

/** 신고 — 대기·검토 중 건수(상태 인덱스 COUNT) + 오래된 대기 사건 limit 건 */
@Component
public class ReportsGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;

    public ReportsGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.REPORTS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        long[] counts = store.reportCounts();
        return new DashboardReportsView(counts[0], counts[1], store.oldestPendingReports(query.limit()));
    }
}

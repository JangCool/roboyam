package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardGroupRequestsView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import org.springframework.stereotype.Component;

/** 소모임 개설 신청 — 대기 건수(상태 인덱스 COUNT) + 최근 신청 limit 건(신청한 회원 닉네임) */
@Component
public class GroupRequestsGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;

    public GroupRequestsGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.GROUP_REQUESTS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardGroupRequestsView(store.pendingGroups(), store.recentGroupRequests(query.limit()));
    }
}

package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardRecentSignupsView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import org.springframework.stereotype.Component;

/** 최근 가입 회원 — 가입 시각 인덱스(ix_users_created) 차례로 limit 건, 탈퇴 제외 */
@Component
public class RecentSignupsGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;

    public RecentSignupsGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.RECENT_SIGNUPS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardRecentSignupsView(store.recentSignups(query.limit()));
    }
}

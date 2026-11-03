package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardGroupBuyProgressView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import org.springframework.stereotype.Component;

/**
 * 모집 중 공구 — 모집 중(RECRUITING) 공구를 지금 마감 시각(연장 중이면 연장 끝) 이른 차례로 limit 개. 달성 = 판매 수량({@code current_qty}) / 목표
 * (성사형 = 최소 수량, 선착순 {@code min_qty = 0} = 최대 수량 — 선착순은 「다 팔림」까지의 진행).
 */
@Component
public class GroupBuyProgressGadget implements DashboardGadgetProvider {

    private final DashboardCommerceStore store;

    public GroupBuyProgressGadget(DashboardCommerceStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.GROUPBUY_PROGRESS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardGroupBuyProgressView(store.recruitingGroupBuys(query.limit()).stream()
                .map(g -> new DashboardGroupBuyProgressView.Item(Long.toString(g.id()), g.title(), g.currentQty(), goal(g.minQty(), g.maxQty()), g.endsAt())).toList());
    }

    /** 달성 목표 — 성사형은 최소 수량, 선착순(최소 0)은 최대 수량 */
    static int goal(int minQty, int maxQty) {
        return minQty > 0 ? minQty : maxQty;
    }
}

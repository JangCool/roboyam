package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardPointShopView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import org.springframework.stereotype.Component;

/** 포인트 상점 — 오늘(한국 날짜) 구매 수(구매 ID 범위 COUNT) + 남은 수량 {@link #LOW_STOCK_MAX}개 이하 판매 중 품목(적은 차례, 최대 5) */
@Component
public class PointShopGadget implements DashboardGadgetProvider {

    /** 품절 임박으로 보는 남은 수량(가정) */
    static final int LOW_STOCK_MAX = 10;
    static final int MAX = 5;

    private final DashboardGadgetStore store;

    public PointShopGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.POINTSHOP;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardPointShopView(store.pointShopPurchasesSince(DashboardGadgetQuery.startOf(query.today())),
                store.lowStockItems(query.now(), LOW_STOCK_MAX, MAX));
    }
}

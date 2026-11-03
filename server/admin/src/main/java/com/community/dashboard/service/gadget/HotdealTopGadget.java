package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardHotdealTopView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.hotdeal.service.DealSettingsReader;
import org.springframework.stereotype.Component;

/** 지금 인기 핫딜 TOP 5 — 최근 7일 등록(글 ID 범위)의 진행 중 딜 · 공개 글을 추천 많은 차례로(핫딜 인기 순위 스냅샷은 없다) */
@Component
public class HotdealTopGadget implements DashboardGadgetProvider {

    static final int TOP = 5;

    private final DashboardGadgetStore store;
    private final DealSettingsReader settings;

    public HotdealTopGadget(DashboardGadgetStore store, DealSettingsReader settings) {
        this.store = store;
        this.settings = settings;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.HOTDEAL_TOP;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardHotdealTopView(store.hotdealTop(query.now(), settings.current().autoExpireDays(), TOP));
    }
}

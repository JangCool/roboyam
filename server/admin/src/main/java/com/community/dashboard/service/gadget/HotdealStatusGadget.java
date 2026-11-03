package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.hotdeal.service.DealSettingsReader;
import org.springframework.stereotype.Component;

/**
 * 핫딜 현황 — 진행 중(관리자 핫딜 목록의 「진행 중」 판정 — 상태 ACTIVE · 마감 전 · 등록 + 자동 종료 일수 안, 확인 필요 포함) · 오늘 등록 · 오늘 마감 예정 · 확인 필요.
 * 진행 중 딜과 오늘 등록 딜만 읽는다(전체 딜을 세지 않는다).
 */
@Component
public class HotdealStatusGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;
    private final DealSettingsReader settings;

    public HotdealStatusGadget(DashboardGadgetStore store, DealSettingsReader settings) {
        this.store = store;
        this.settings = settings;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.HOTDEAL_STATUS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return store.hotdealStatus(query.now(), DashboardGadgetQuery.startOf(query.today()), DashboardGadgetQuery.startOf(query.today().plusDays(1)),
                settings.current().autoExpireDays());
    }
}

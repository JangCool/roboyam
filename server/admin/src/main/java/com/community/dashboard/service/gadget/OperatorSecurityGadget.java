package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import org.springframework.stereotype.Component;

/** 운영자 보안 — 운영자 수 · 잠긴 계정 · 2차 인증 미등록(운영자 표는 작다) */
@Component
public class OperatorSecurityGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;

    public OperatorSecurityGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.OPERATOR_SECURITY;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return store.operatorSecurity();
    }
}

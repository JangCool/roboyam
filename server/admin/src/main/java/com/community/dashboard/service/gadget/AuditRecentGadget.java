package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardAuditRecentView;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import org.springframework.stereotype.Component;

/**
 * 최근 운영자 활동 — 성공한 운영자 작업 limit 건(발생 시각 인덱스 차례). {@code summary} = 요청 언어의 작업 이름({@code audit.action.<코드>}, 번들에 없으면 코드).
 * 거절(DENIED)·실패 기록은 넣지 않는다(권한 거절이 목록을 덮지 않게). 조회 자체는 기록하지 않는다.
 */
@Component
public class AuditRecentGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;
    private final Messages messages;

    public AuditRecentGadget(DashboardGadgetStore store, Messages messages) {
        this.store = store;
        this.messages = messages;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.AUDIT_RECENT;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardAuditRecentView(store.recentOperatorAudits(query.limit()).stream().map(r -> {
            String label = messages.find(new MessageRef("audit.action." + r.action()));
            return new DashboardAuditRecentView.Item(Long.toString(r.id()), r.actorName() == null ? "" : r.actorName(), label == null ? r.action() : label, r.at());
        }).toList());
    }
}

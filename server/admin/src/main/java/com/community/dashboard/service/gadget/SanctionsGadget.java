package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardSanctionsView;
import com.community.sanction.repository.master.AdminSanctionStore;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import org.springframework.stereotype.Component;

/**
 * 제재 — 진행 중 정지·영구 정지 수(부분 색인 COUNT) · 이의제기 대기 수(부분 색인 COUNT) · 대기 오래된 차례 3건(같은 색인 차례). 제재 이름은 요청 언어(캐시 키에 언어가 있다).
 */
@Component
public class SanctionsGadget implements DashboardGadgetProvider {

    static final int APPEALS = 3;

    private final AdminSanctionStore store;
    private final Messages messages;

    public SanctionsGadget(AdminSanctionStore store, Messages messages) {
        this.store = store;
        this.messages = messages;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.SANCTIONS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        return new DashboardSanctionsView(store.running(query.now()), store.pendingAppeals(), store.oldestPending(APPEALS).stream()
                .map(r -> new DashboardSanctionsView.Appeal(Long.toString(r.id()), r.nickname() == null ? "" : r.nickname(), kindName(r), r.appealedAt()))
                .toList());
    }

    private String kindName(AdminSanctionStore.PendingAppealRow r) {
        if (r.kind().isWarning() && r.warningNo() != null) {
            return messages.get(new MessageRef("member.sanction.kind.WARNING.n", String.valueOf(r.warningNo())));
        }
        return messages.get(new MessageRef("member.sanction.kind." + r.kind().name()));
    }
}

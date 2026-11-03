package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardReconcileView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 결제 대사 — 최근 대사일·상태 · 열린 대사 이슈 수 · 최근 24시간 웹훅 처리 실패. 상태는 「운영자가 할 일이 있나」를 먼저 본다: 열린 이슈가 있으면 MISMATCHED(최근 날이 일치여도),
 * 없으면 최근 날이 끝났으면(DONE · 처리를 마친 MISMATCH) MATCHED, 대사 전·도는 중·실패면 PENDING.
 */
@Component
public class ReconcileGadget implements DashboardGadgetProvider {

    static final Duration WEBHOOK_WINDOW = Duration.ofHours(24);

    private final DashboardCommerceStore store;

    public ReconcileGadget(DashboardCommerceStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.RECONCILE;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        Optional<DashboardCommerceStore.ReconcileDay> last = store.latestReconcileDay();
        long open = store.openReconcileIssues();
        String dayStatus = last.map(DashboardCommerceStore.ReconcileDay::status).orElse(null);
        return new DashboardReconcileView(last.map(DashboardCommerceStore.ReconcileDay::day).orElse(null), status(dayStatus, open), open,
                store.webhookFailuresSince(query.now().minus(WEBHOOK_WINDOW)));
    }

    /** 화면 상태 — {@code dayStatus} = payment_reconcile_days 상태(RUNNING · DONE · MISMATCH · FAILED, 없으면 null) */
    public static String status(String dayStatus, long openIssues) {
        if (openIssues > 0) {
            return "MISMATCHED";
        }
        return "DONE".equals(dayStatus) || "MISMATCH".equals(dayStatus) ? "MATCHED" : "PENDING";
    }
}

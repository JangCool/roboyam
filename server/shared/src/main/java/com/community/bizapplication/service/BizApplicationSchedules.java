package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시각에 걸린 신청 건 처리(배치가 건마다 부른다 — 설계 8절). 모두 멱등: 조건이 이미 거짓이면(다른 길이 바꿨으면) 아무것도 하지 않고 false.
 *
 * <ul>
 *   <li>결제 기한 지남: 열린 결제를 먼저 닫고(결제 행) 신청 건 행을 잠근 뒤 아직 결제 대기이고 기한이 지났으면 EXPIRED(자리 반납) + 사업자 알림.</li>
 *   <li>결제 기한 임박(24시간 안) · 시작 임박 · 종료 임박: 아직 알리지 않았으면 표시하고 알림 한 번.</li>
 * </ul>
 */
@Service
public class BizApplicationSchedules {

    private final BizApplicationStore store;
    private final BizApplicationOps ops;

    public BizApplicationSchedules(BizApplicationStore store, BizApplicationOps ops) {
        this.store = store;
        this.ops = ops;
    }

    @Transactional
    public boolean expire(long id) {
        AppRow app = ops.closePaymentsThenLock(id, null, "BIZ_PAYMENT_EXPIRED");
        Instant now = ops.now();
        if (app.status() != BizApplicationStatus.AWAITING_PAYMENT || app.paymentDeadlineAt() == null || app.paymentDeadlineAt().isAfter(now)) {
            return false;
        }
        ops.move(app, Action.EXPIRE, now);
        ops.event(id, BizEventAction.EXPIRED, BizActorType.SYSTEM, null, null, now);
        ops.notices().member(app, BizApplicationNotices.Kind.EXPIRED, app.approved().total(), null);
        return true;
    }

    @Transactional
    public boolean noticePaymentDue(AppRow app) {
        if (!store.markNotified(app.id(), "payment_due_notified_at", ops.now())) {
            return false;
        }
        ops.notices().member(app, BizApplicationNotices.Kind.PAYMENT_DUE, app.approved().total(), BizPeriods.kstMinute(app.paymentDeadlineAt()));
        return true;
    }

    @Transactional
    public boolean noticeStart(AppRow app) {
        if (!store.markNotified(app.id(), "start_notified_at", ops.now())) {
            return false;
        }
        ops.notices().member(app, BizApplicationNotices.Kind.START_SOON, 0, ServiceTime.dateOf(app.startAt()).toString());
        return true;
    }

    @Transactional
    public boolean noticeEnd(AppRow app) {
        if (!store.markNotified(app.id(), "end_notified_at", ops.now())) {
            return false;
        }
        ops.notices().member(app, BizApplicationNotices.Kind.END_SOON, 0, BizPeriods.endDate(app.endAt()).toString());
        return true;
    }
}

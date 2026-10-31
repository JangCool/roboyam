package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.entity.BizTakedownCause;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.ItemRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.business.dto.BusinessSuspendPlan;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.service.BusinessApplicationHook;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 사업자 신원 → 신청 건 훅 구현(설계 9절 · H2 · F8). 정지·해제·탈퇴는 <b>신원 처리 트랜잭션 안에서</b> 불린다(실패하면 신원 처리도 되돌아간다).
 *
 * <ul>
 *   <li>정지: 결제 전 건 {@code CANCEL} 취소(입금 기록이 있으면 수동 환불 송금 대기) / {@code KEEP} 그대로 · 시작 전 예약 {@code REFUND_FULL} 취소 + 전액 환불 /
 *       {@code HOLD} 내림(SUSPENDED) · 진행 중 {@code TAKEDOWN_PRORATE} 내림 + 일할 환불 심사 대기 / {@code TAKEDOWN_ONLY} 내림.</li>
 *   <li>해제: 정지로 내린 건 — {@code EXTEND_RESTORE} 내린 시간만큼 끝을 늘려(자리 잠금·판정, 꽉 차면 늘리지 않고 되돌리고 기록) 예약으로, 정지로 생긴 심사 대기 환불은 거절(「해제로 되돌림」)
 *       / {@code KEEP_REFUND} 심사 대기 환불이 있는 건은 그대로(운영자가 환불 심사), 환불이 없는 건(시작 전 보류·내림만)은 늘리지 않고 되돌린다(가정 — 그대로 두면 되돌릴 길이 없다).</li>
 *   <li>탈퇴: 진행 중(내림 포함, 끝나지 않은) 건이 있으면 막는다. 탈퇴 처리 — 결제 전 건 취소, 시작 전 전액 환불, {@code forfeitRunning} 이면 진행 중 건 취소(환불 없음, 처리 기록 FORFEITED).</li>
 * </ul>
 */
@Component
public class BizApplicationHookImpl implements BusinessApplicationHook {

    private static final Set<BizApplicationStatus> PENDING = EnumSet.of(BizApplicationStatus.SUBMITTED, BizApplicationStatus.AWAITING_PAYMENT,
            BizApplicationStatus.AWAITING_DEPOSIT);
    private static final Set<BizApplicationStatus> LIVE = EnumSet.of(BizApplicationStatus.SCHEDULED, BizApplicationStatus.TAKEN_DOWN);

    private final BizApplicationStore store;
    private final BizRefundStore refunds;
    private final BizSlotService slots;
    private final BizApplicationOps ops;

    public BizApplicationHookImpl(BizApplicationStore store, BizRefundStore refunds, BizSlotService slots, BizApplicationOps ops) {
        this.store = store;
        this.refunds = refunds;
        this.slots = slots;
        this.ops = ops;
    }

    @Override
    public SuspendImpact impact(long profileId) {
        Instant now = ops.now();
        int pending = store.byProfile(profileId, PENDING).size();
        int scheduled = 0;
        int running = 0;
        for (AppRow app : store.byProfile(profileId, EnumSet.of(BizApplicationStatus.SCHEDULED))) {
            switch (BizPhase.of(app.startAt(), app.endAt(), now)) {
                case UPCOMING -> scheduled++;
                case RUNNING -> running++;
                case ENDED -> {
                }
            }
        }
        return new SuspendImpact(pending, scheduled, running);
    }

    @Override
    public ServiceCounts counts(long profileId, BusinessServiceKind service) {
        Instant now = ops.now();
        int live = (int) store.byProfile(profileId, LIVE).stream().filter(a -> a.kind().service() == service).filter(a -> a.endAt().isAfter(now)).count();
        int pending = (int) store.byProfile(profileId, PENDING).stream().filter(a -> a.kind().service() == service).count();
        return new ServiceCounts(live, pending);
    }

    @Override
    public void onSuspended(long profileId, BusinessSuspendPlan plan, long operatorId) {
        Instant now = ops.now();
        if (plan.pending() == BusinessSuspendPlan.Pending.CANCEL) {
            for (AppRow seen : store.byProfile(profileId, PENDING)) {
                AppRow app = ops.closePaymentsThenLock(seen.id(), null, "BIZ_SUSPENDED");
                if (PENDING.contains(app.status())) {
                    ops.cancelBeforePayment(app, Action.SYSTEM_CANCEL, BizActorType.OPERATOR, operatorId, "SUSPENDED", now);
                    ops.notices().member(app, BizApplicationNotices.Kind.CANCELED, 0, null);
                }
            }
        }
        for (AppRow seen : store.byProfile(profileId, EnumSet.of(BizApplicationStatus.SCHEDULED))) {
            AppRow app = store.lock(seen.id()).orElseThrow();
            if (app.status() != BizApplicationStatus.SCHEDULED) {
                continue;
            }
            switch (BizPhase.of(app.startAt(), app.endAt(), now)) {
                case UPCOMING -> {
                    if (plan.scheduled() == BusinessSuspendPlan.Scheduled.REFUND_FULL) {
                        long paid = ops.paidAmount(app);
                        ops.cancelWithFullRefund(app, BizActorType.OPERATOR, operatorId, "SUSPENDED", now);
                        ops.notices().member(app, BizApplicationNotices.Kind.REFUND_APPROVED, paid, null);
                    } else {
                        takeDown(app, operatorId, now);
                    }
                }
                case RUNNING -> {
                    takeDown(app, operatorId, now);
                    if (plan.running() == BusinessSuspendPlan.Running.TAKEDOWN_PRORATE && refunds.lockOpen(app.id()).isEmpty()) {
                        ops.requestProrated(app, BizRefundType.SUSPEND_PRORATED, "SUSPENDED", now);
                        ops.event(app.id(), BizEventAction.REFUND_REQUESTED, BizActorType.SYSTEM, null, "SUSPENDED", now);
                        ops.notices().refundRequested(app);
                    }
                }
                case ENDED -> {
                }
            }
        }
    }

    private void takeDown(AppRow app, long operatorId, Instant now) {
        if (!store.takeDown(app.id(), BizTakedownCause.SUSPENDED, null, now)) {
            return;
        }
        ops.event(app.id(), BizEventAction.TAKEN_DOWN, BizActorType.OPERATOR, operatorId, "SUSPENDED", now);
        ops.notices().member(app, BizApplicationNotices.Kind.TAKEN_DOWN, 0, null);
        ops.closedAfterSchedule(app);
    }

    @Override
    public void onUnsuspended(long profileId, BusinessSuspendPlan.Restore restore, long operatorId) {
        Instant now = ops.now();
        for (AppRow seen : store.byProfile(profileId, EnumSet.of(BizApplicationStatus.TAKEN_DOWN))) {
            AppRow app = store.lock(seen.id()).orElseThrow();
            if (app.status() != BizApplicationStatus.TAKEN_DOWN || app.takedownCause() != BizTakedownCause.SUSPENDED) {
                continue;
            }
            Optional<RefundRow> open = refunds.lockOpen(app.id());
            if (restore == BusinessSuspendPlan.Restore.KEEP_REFUND) {
                if (open.isPresent()) {
                    continue; // 운영자가 환불을 심사한다
                }
                restoreAs(app, app.endAt(), operatorId, null, now);
                continue;
            }
            Instant from = app.takenDownAt().isAfter(app.startAt()) ? app.takenDownAt() : app.startAt();
            Duration lost = now.isAfter(from) ? Duration.between(from, now).truncatedTo(java.time.temporal.ChronoUnit.MINUTES) : Duration.ZERO;
            Instant newEnd = app.endAt().plus(lost);
            String note = lost.isZero() ? null : "EXTENDED:" + lost.toMinutes();
            if (!lost.isZero()) {
                List<BizProduct> products = store.items(app.id()).stream().map(ItemRow::product).toList();
                if (!slots.lockAndCheck(products, app.startAt(), newEnd, app.id())) {
                    newEnd = app.endAt();
                    note = "EXTEND_FAILED";
                }
            }
            if (open.isPresent() && open.get().type() == BizRefundType.SUSPEND_PRORATED) {
                refunds.decide(open.get().id(), BizRefundStatus.DENIED, null, "RESTORED", null, null, operatorId, now);
                ops.event(app.id(), BizEventAction.REFUND_DENIED, BizActorType.OPERATOR, operatorId, "RESTORED", now);
            }
            restoreAs(app, newEnd, operatorId, note, now);
        }
    }

    private void restoreAs(AppRow app, Instant newEnd, long operatorId, String note, Instant now) {
        if (!store.restore(app.id(), newEnd, now)) {
            return;
        }
        if (!newEnd.equals(app.endAt())) {
            store.extendItems(app.id(), newEnd);
        }
        ops.event(app.id(), BizEventAction.RESTORED, BizActorType.OPERATOR, operatorId, note, now);
        ops.notices().member(app, BizApplicationNotices.Kind.RESTORED, 0, note);
        ops.scheduled(app);
    }

    @Override
    public WithdrawalCheck withdrawalCheck(long profileId) {
        Instant now = ops.now();
        int running = (int) store.byProfile(profileId, LIVE).stream().filter(a -> running(a, now)).count();
        return new WithdrawalCheck(running > 0, running);
    }

    /** 진행 중 — 예약이고 시작 ≤ 지금 < 끝, 또는 끝나지 않은 내림 */
    private static boolean running(AppRow a, Instant now) {
        if (a.status() == BizApplicationStatus.TAKEN_DOWN) {
            return a.endAt().isAfter(now);
        }
        return BizPhase.of(a.startAt(), a.endAt(), now) == BizPhase.RUNNING;
    }

    @Override
    public void onWithdrawn(long profileId, boolean forfeitRunning) {
        Instant now = ops.now();
        for (AppRow seen : store.byProfile(profileId, PENDING)) {
            AppRow app = ops.closePaymentsThenLock(seen.id(), null, "BIZ_WITHDRAWN");
            if (PENDING.contains(app.status())) {
                ops.cancelBeforePayment(app, Action.SYSTEM_CANCEL, BizActorType.SYSTEM, null, "WITHDRAWN", now);
            }
        }
        for (AppRow seen : store.byProfile(profileId, LIVE)) {
            AppRow app = store.lock(seen.id()).orElseThrow();
            if (!LIVE.contains(app.status())) {
                continue;
            }
            boolean upcoming = app.startAt().isAfter(now);
            if (upcoming) {
                ops.cancelWithFullRefund(app, BizActorType.SYSTEM, null, "WITHDRAWN", now);
            } else if (forfeitRunning && running(app, now)) {
                refunds.lockOpen(app.id()).ifPresent(r -> refunds.decide(r.id(), BizRefundStatus.DENIED, null, "FORFEITED", null, null, null, now));
                ops.move(app, Action.FORFEIT, now);
                ops.event(app.id(), BizEventAction.FORFEITED, BizActorType.MEMBER, app.userId(), null, now);
                ops.closedAfterSchedule(app);
            }
        }
    }

    /** 종류가 그 서비스인가(시험·문서용) */
    static boolean of(BizApplicationKind kind, BusinessServiceKind service) {
        return kind.service() == service;
    }
}

package com.community.bizapplication.service;

import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizApplicationStore.DepositRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.NewRefund;
import com.community.bizapplication.service.BizTransitions.Action;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.service.PaymentCancelService;
import com.community.payment.service.PaymentOrderService;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 신청 건 처리의 공통 조각 — 사업자 처리·운영자 처리·정지 훅·배치·결제 처리기가 같이 쓴다. 모두 <b>부르는 쪽의 트랜잭션 안</b>에서 돈다.
 *
 * <ul>
 *   <li><b>잠금 차례</b>: 결제 대기 건을 닫는 길(만료·취소·수동 전환·소재 바꾸기·정지)은 {@link #closePaymentsThenLock} — 결제 모듈의 열린 결제(READY)를 먼저 닫고(결제 행) 그다음
 *       신청 건 행을 잠근다. 결제 확정이 결제 행 → 신청 건 행 차례라 같은 차례가 된다(공구 결정 37).</li>
 *   <li><b>환불</b>: 시작 전 전액(FULL_AUTO, 바로 승인)과 진행 중 일할 심사(PRORATED·SUSPEND_PRORATED, 심사 대기). 돈은 온라인이면 결제 모듈 취소 대기열(요청 키
 *       {@code BIZ_REFUND:<환불 ID>}), 수동이면 운영자 송금 표시(PENDING → DONE)로 돌아간다.</li>
 * </ul>
 */
@Component
public class BizApplicationOps {

    /** 감사 대상 종류 */
    public static final String AUDIT_TARGET = "BIZ_APPLICATION";

    private final BizApplicationStore store;
    private final BizRefundStore refunds;
    /** 결제 모듈의 처리기 목록이 이 클래스를 쓰는 처리기를 담고 있어 늦게 꺼낸다(순환 의존 끊기) */
    private final ObjectProvider<PaymentOrderService> payments;
    private final PaymentCancelService cancels;
    private final BizApplicationNotices notices;
    private final AuditLogService audit;
    private final ObjectProvider<BizHotdealPosting> hotdeals;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public BizApplicationOps(BizApplicationStore store, BizRefundStore refunds, ObjectProvider<PaymentOrderService> payments, PaymentCancelService cancels, BizApplicationNotices notices,
                             AuditLogService audit, ObjectProvider<BizHotdealPosting> hotdeals, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.store = store;
        this.refunds = refunds;
        this.payments = payments;
        this.cancels = cancels;
        this.notices = notices;
        this.audit = audit;
        this.hotdeals = hotdeals;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public Clock clock() {
        return clock;
    }

    public long nextId() {
        return ids.nextId();
    }

    public BizApplicationNotices notices() {
        return notices;
    }

    public void event(long applicationId, BizEventAction action, BizActorType actorType, Long actorId, String note, Instant now) {
        store.insertEvent(ids.nextId(), applicationId, action, actorType, actorId, note, now);
    }

    /** 감사 — 운영자가 한 일(업무 트랜잭션 안) */
    public void audit(long operatorId, String ip, String action, long applicationId, Map<String, Object> detail) {
        audit.record(new AuditEvent(AuditActorType.OPERATOR, operatorId, action, AuditResult.SUCCESS, AUDIT_TARGET, Long.toString(applicationId), ip, detail));
    }

    /**
     * 열린 결제(READY)를 먼저 닫고 신청 건 행을 잠근다. {@code userId} 가 있으면 그 회원의 건만(남의 건은 404 — 결제도 건드리지 않는다).
     *
     * @param reason 결제 이력에 남길 까닭(대문자·밑줄)
     */
    public AppRow closePaymentsThenLock(long applicationId, Long userId, String reason) {
        if (userId != null && store.findOwned(applicationId, userId).isEmpty()) {
            throw BizApplicationException.notFound();
        }
        if (userId == null && store.find(applicationId).isEmpty()) {
            throw BizApplicationException.notFound();
        }
        payments.getObject().expireOpen(PaymentOrderType.BIZ_APPLICATION, applicationId, reason);
        Optional<AppRow> locked = userId == null ? store.lock(applicationId) : store.lockOwned(applicationId, userId);
        return locked.orElseThrow(BizApplicationException::notFound);
    }

    /** 상태 바꾸기 — 전이 표에 없거나 조건부 UPDATE 에 지면 409 */
    public BizApplicationStatus move(AppRow app, Action action, Instant now) {
        BizApplicationStatus to = BizTransitions.require(action, app.status());
        if (!store.move(app.id(), app.status(), to, now)) {
            throw BizApplicationException.stateChanged();
        }
        return to;
    }

    /** 돌려줄 결제액 — 온라인 = 확정 합계(결제 금액), 수동 = 입금 확인 금액(없으면 0) */
    public long paidAmount(AppRow app) {
        if (app.paymentMethod() == BizPaymentMethod.ONLINE) {
            return app.paidAt() == null ? 0 : app.approved().total();
        }
        return store.deposit(app.id()).map(DepositRow::amount).orElse(0L);
    }

    /**
     * 결제 전 건(심사·결제·입금 대기) 취소 — 입금 기록이 있는 입금 대기면 수동 전액 환불(송금 대기)을 남긴다(운영자 취소·정지·탈퇴). 부르기 전에 {@link #closePaymentsThenLock}.
     */
    public void cancelBeforePayment(AppRow app, Action action, BizActorType actorType, Long actorId, String reason, Instant now) {
        move(app, action, now);
        event(app.id(), BizEventAction.CANCELED, actorType, actorId, reason, now);
        if (app.status() == BizApplicationStatus.AWAITING_DEPOSIT) {
            store.deposit(app.id()).ifPresent(d -> insertFullRefund(app, BizPaymentMethod.MANUAL, d.amount(), reason, actorId, now));
        }
    }

    /**
     * 시작 전 예약(·내림) 취소 + 전액 환불(H1) — 온라인은 결제 모듈 취소, 수동은 송금 대기. 돌려준 환불 줄 ID.
     */
    public long cancelWithFullRefund(AppRow app, BizActorType actorType, Long actorId, String reason, Instant now) {
        move(app, Action.REFUND_FULL, now);
        event(app.id(), BizEventAction.CANCELED, actorType, actorId, reason, now);
        long refundId = insertFullRefund(app, app.paymentMethod(), paidAmount(app), reason, actorId, now);
        closedAfterSchedule(app);
        return refundId;
    }

    /** 예약 확정 — 핫딜 글이면 게시 고리 */
    public void scheduled(AppRow app) {
        if (app.kind() == com.community.bizapplication.entity.BizApplicationKind.HOTDEAL_POST) {
            hotdeals.getIfAvailable(() -> BizHotdealPosting.NONE).onScheduled(app.id());
        }
    }

    /** 예약·내림 건이 취소·내림됐다 — 핫딜 글이면 내림 고리 */
    public void closedAfterSchedule(AppRow app) {
        if (app.kind() == com.community.bizapplication.entity.BizApplicationKind.HOTDEAL_POST) {
            hotdeals.getIfAvailable(() -> BizHotdealPosting.NONE).onClosed(app.id());
        }
    }

    private long insertFullRefund(AppRow app, BizPaymentMethod method, long paid, String reason, Long actorId, Instant now) {
        long refundId = ids.nextId();
        boolean manual = method == BizPaymentMethod.MANUAL;
        int total = BizPricing.serviceDays(app.startAt(), app.endAt());
        refunds.insert(new NewRefund(refundId, app.id(), BizRefundType.FULL_AUTO, BizRefundStatus.APPROVED, method, paid, paid, 0, total, paid, cut(reason, 300),
                manual && paid > 0 ? BizRefundStore.TRANSFER_PENDING : null, now, now, actorId, paid == 0 ? now : null));
        event(app.id(), BizEventAction.REFUND_APPROVED, actorId == null ? BizActorType.SYSTEM : BizActorType.OPERATOR, actorId, String.valueOf(paid), now);
        if (!manual && paid > 0) {
            requestPgCancel(app, refundId, paid, actorId);
        }
        return refundId;
    }

    /** 진행 중 일할 환불 심사 대기 줄(제안액) — 열린 줄이 이미 있으면 409 */
    public long requestProrated(AppRow app, BizRefundType type, String reason, Instant now) {
        long paid = paidAmount(app);
        BizPricing.Prorate p = BizPricing.prorate(paid, app.startAt(), app.endAt(), now);
        long refundId = ids.nextId();
        try {
            refunds.insert(new NewRefund(refundId, app.id(), type, BizRefundStatus.REQUESTED, app.paymentMethod(), paid, p.proposed(), p.usedDays(), p.totalDays(), null,
                    cut(reason, 300), null, now, null, null, null));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw BizApplicationException.stateChanged();
        }
        return refundId;
    }

    /** 승인한 환불의 돈 — 온라인 금액 > 0 이면 결제 모듈 취소, 수동은 이미 송금 대기로 둔 상태 */
    public void requestPgCancel(AppRow app, long refundId, long amount, Long operatorId) {
        if (app.paymentOrderId() == null) {
            throw BizApplicationException.stateChanged();
        }
        long cancelId = cancels.request(new PaymentCancelOrder(app.paymentOrderId(), amount, CancelKind.REFUND, "사업자 신청 건 환불", "BIZ_REFUND:" + refundId, operatorId));
        refunds.linkCancel(refundId, cancelId);
    }

    static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

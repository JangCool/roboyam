package com.community.groupbuy.handler;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.entity.ExpireCause;
import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.repository.master.GroupBuyRefundStore;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.repository.master.ParticipationStore.ItemRow;
import com.community.groupbuy.repository.master.ParticipationStore.ParticipationRow;
import com.community.groupbuy.service.GroupBuyQtyService;
import com.community.groupbuy.service.GroupBuySavepoint;
import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.handler.PaymentOrderHandler;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 공구 결제 처리기({@code GROUP_BUY} — commerce.md 8-6, 계약 「패키지」 handler·결정 27·37·38·40·62·78·82). 결제 확정·취소 완료 트랜잭션 안에서 결제 행을 쥔 채 불린다.
 *
 * <ul>
 *   <li><b>{@code onPaid} 는 도메인 사정으로 예외를 던지지 않는다</b>(결정 62 — 예외면 결제 확정이 영원히 재시도된다): 주문 없음·결제 중 아님·다른 결제·선점 시간 뒤(포트원 {@code paidAt} 과 비교)·
 *       금액 불일치·공구가 결제를 받지 않는 상태(판정·중단 뒤 — P3b)·수량 불변식 깨짐(결정 82)·DB 제약 충돌은 모두 REJECT(자동 취소). 예외는 DB 장애 같은 인프라 오류뿐.</li>
 *   <li>공구·단품 행을 {@code FOR UPDATE} 하지 않고 조건부 UPDATE 만 쓴다(결정 37). 주문 행은 결제 행 다음에 잠근다(결제 확정이 결제 행 → 주문 차례).</li>
 *   <li>받아들이면: 수량 확정(CONFIRM) → 주문 JOINED(즉시 발송 공구는 CONFIRMED 까지 한 번에 — 10-3) → 다 팔리면 같은 트랜잭션에서 CLOSING(결정 27·78).
 *       이 일들은 세이브포인트 안이라 중간에 지면 되돌리고 REJECT 로 간다.</li>
 *   <li>REJECT 경로의 주문 만료 + 선점 해제는 <b>주문 조건부 전이가 이긴 쪽만</b>(선점 만료 워커와 이중 해제 없음). 이 만료는 이탈 횟수에 세지 않는다(돈을 낸 회원).</li>
 *   <li>{@code onCanceled} 는 <b>표시·알림만</b> — 환불 누계는 취소를 넣을 때 이미 올렸다(결정 38). 환불 기록을 완료로 두고 이벤트. 자동 취소(늦은 결제·도메인 거절)는 주문 금액·상태를
 *       건드리지 않고 회원 안내만(결정 40). 주문 행을 잠그지 않는다(취소를 넣는 흐름이 주문 행 → 결제 행 차례라 교착이 된다).</li>
 * </ul>
 */
@Component
public class GroupBuyPaymentHandler implements PaymentOrderHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyPaymentHandler.class);

    /** 거절 사유 코드(결제 이력에 남는다) */
    public static final String REJECT_ORDER_NOT_FOUND = "GROUP_BUY_ORDER_NOT_FOUND";
    public static final String REJECT_NOT_PENDING = "GROUP_BUY_ORDER_NOT_PENDING";
    public static final String REJECT_PAYMENT_MISMATCH = "GROUP_BUY_PAYMENT_MISMATCH";
    public static final String REJECT_HOLD_EXPIRED = "GROUP_BUY_HOLD_EXPIRED";
    public static final String REJECT_AMOUNT_MISMATCH = "GROUP_BUY_AMOUNT_MISMATCH";
    public static final String REJECT_CLOSED = "GROUP_BUY_CLOSED";
    public static final String REJECT_QTY_ANOMALY = "GROUP_BUY_QTY_ANOMALY";
    public static final String REJECT_CONFLICT = "GROUP_BUY_CONFLICT";

    private final ParticipationStore participations;
    private final GroupBuyStore groups;
    private final GroupBuyRefundStore refunds;
    private final GroupBuyQtyService qty;
    private final GroupBuySavepoint savepoint;
    private final SnowflakeIdGenerator ids;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public GroupBuyPaymentHandler(ParticipationStore participations, GroupBuyStore groups, GroupBuyRefundStore refunds, GroupBuyQtyService qty, GroupBuySavepoint savepoint,
                                  SnowflakeIdGenerator ids, ApplicationEventPublisher events, ObjectProvider<Clock> clocks) {
        this.participations = participations;
        this.groups = groups;
        this.refunds = refunds;
        this.qty = qty;
        this.savepoint = savepoint;
        this.ids = ids;
        this.events = events;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Override
    public PaymentOrderType orderType() {
        return PaymentOrderType.GROUP_BUY;
    }

    @Override
    public PaymentDecision onPaid(PaidPayment payment) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Optional<ParticipationRow> locked = participations.lock(payment.orderRefId());
        if (locked.isEmpty()) {
            log.error("결제된 공구 주문이 없다 — 거절 paymentOrderId={}", payment.paymentOrderId());
            return PaymentDecision.reject(REJECT_ORDER_NOT_FOUND);
        }
        ParticipationRow p = locked.get();
        if (p.status() != ParticipationStatus.PENDING_PAYMENT) {
            return reject(p, REJECT_NOT_PENDING, null, now);
        }
        if (p.paymentOrderId() != null && p.paymentOrderId() != payment.paymentOrderId()) {
            return reject(p, REJECT_PAYMENT_MISMATCH, null, now);
        }
        if (payment.paidAt() != null && payment.paidAt().isAfter(p.reservedUntil())) {
            return reject(p, REJECT_HOLD_EXPIRED, ExpireCause.LATE_PAYMENT, now);
        }
        if (payment.amount() != p.cashAmount()) {
            return reject(p, REJECT_AMOUNT_MISMATCH, ExpireCause.REJECTED_ANOMALY, now);
        }
        List<ItemRow> items = participations.items(p.id());
        String failure;
        try {
            failure = savepoint.run(() -> accept(p, items, payment, now));
        } catch (Rejected e) {
            failure = e.reason;
        } catch (DataIntegrityViolationException | CommonException e) {
            log.warn("공구 결제 확정이 도메인 충돌로 되돌려졌다 — 거절 participationId={} 원인={}", p.id(), e.getClass().getSimpleName());
            failure = REJECT_CONFLICT;
        }
        if (failure == null) {
            events.publishEvent(new GroupBuyEvents.OrderPaid(p.id(), p.groupBuyId(), p.userId()));
            return PaymentDecision.accept();
        }
        return reject(p, failure, REJECT_CLOSED.equals(failure) ? ExpireCause.REJECTED_CLOSED : ExpireCause.REJECTED_ANOMALY, now);
    }

    /** 받아들이기(세이브포인트 안) — 실패 사유를 돌려주면 부르는 쪽이 되돌린다. 성공이면 null */
    private String accept(ParticipationRow p, List<ItemRow> items, PaidPayment payment, Instant now) {
        GroupBuyRow group = groups.find(p.groupBuyId()).orElseThrow();
        if (!qty.confirm(group.id(), p.id(), items, now)) {
            GroupBuyRow fresh = groups.find(group.id()).orElseThrow();
            String reason = GroupBuyStatus.ACCEPTS_PAYMENT.contains(fresh.status()) ? REJECT_QTY_ANOMALY : REJECT_CLOSED;
            if (REJECT_QTY_ANOMALY.equals(reason)) {
                log.error("결제 확정할 선점이 모자란다 — 불변식이 깨졌다 groupBuyId={} participationId={}", group.id(), p.id());
            }
            throw new Rejected(reason);
        }
        ParticipationStatus to = group.immediate() ? ParticipationStatus.CONFIRMED : ParticipationStatus.JOINED;
        if (!participations.markPaid(p.id(), to, payment.paymentOrderId(), payment.paidAt() == null ? now : payment.paidAt(), group.revisionNo(), now)) {
            throw new Rejected(REJECT_NOT_PENDING);
        }
        participations.insertHistory(ids.nextId(), p, ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.JOINED, null, null, now);
        if (to == ParticipationStatus.CONFIRMED) {
            participations.insertHistory(ids.nextId(), p, ParticipationStatus.JOINED, ParticipationStatus.CONFIRMED, "IMMEDIATE", null, now);
        }
        qty.closeIfSoldOut(group.id(), null, now);
        return null;
    }

    /**
     * 거절 — {@code cause} 가 있으면 주문을 만료시키고(조건부 — 이긴 쪽만) 선점을 해제한다. 이 일도 세이브포인트 안에서 하고, 거기서 또 지면 로그만(거절은 그대로).
     */
    private PaymentDecision reject(ParticipationRow p, String reason, ExpireCause cause, Instant now) {
        if (cause != null) {
            try {
                savepoint.run(() -> {
                    if (participations.expire(p.id(), cause, null, now)) {
                        qty.release(p.groupBuyId(), p.id(), participations.items(p.id()), now);
                        participations.insertHistory(ids.nextId(), p, ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.EXPIRED, cause.name(), null, now);
                    }
                    return null;
                });
            } catch (DataIntegrityViolationException | CommonException | IllegalStateException e) {
                log.error("거절한 공구 주문을 닫지 못했다 — 선점 만료 워커가 닫는다 participationId={} 원인={}", p.id(), e.getClass().getSimpleName());
            }
        }
        log.warn("공구 결제를 거절했다 participationId={} reason={}", p.id(), reason);
        events.publishEvent(new GroupBuyEvents.OrderRejected(p.id(), p.groupBuyId(), p.userId(), reason, REJECT_QTY_ANOMALY.equals(reason)));
        return PaymentDecision.reject(reason);
    }

    @Override
    public void onCanceled(CanceledPayment cancellation) {
        if (cancellation.anomaly() != null) {
            events.publishEvent(new GroupBuyEvents.AutoCancelCompleted(cancellation.orderRefId(), cancellation.userId(), cancellation.paymentOrderId()));
            return;
        }
        refunds.completeByCancel(cancellation.cancelId(), clock.instant().truncatedTo(ChronoUnit.MICROS))
                .ifPresent(refund -> events.publishEvent(new GroupBuyEvents.RefundCompleted(refund.id(), refund.participationId(), refund.userId())));
    }

    /** 받아들이기를 되돌리는 도메인 사정(세이브포인트 밖으로 사유를 싣는다) */
    static final class Rejected extends CommonException {

        final String reason;

        Rejected(String reason) {
            super(com.community.zconfiguration.exception.ErrorCode.INTERNAL);
            this.reason = reason;
        }
    }
}

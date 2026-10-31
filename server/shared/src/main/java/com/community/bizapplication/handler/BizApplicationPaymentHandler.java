package com.community.bizapplication.handler;

import com.community.bizapplication.entity.BizActorType;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizEventAction;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.service.BizApplicationNotices;
import com.community.bizapplication.service.BizApplicationOps;
import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.ManualCancelResolution;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.handler.PaymentOrderHandler;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 사업자 신청 건 결제 처리기({@code BIZ_APPLICATION} — 설계 5절, commerce.md 8-6). 결제 확정·취소 완료 트랜잭션 안에서 결제 행을 쥔 채 불린다.
 *
 * <ul>
 *   <li><b>{@code onPaid} 는 도메인 사정으로 예외를 던지지 않는다</b>(공구 결정 62 — 예외면 결제 확정이 영원히 재시도된다): 신청 건 없음·결제 대기 아님·온라인 아님·다른 회원·
 *       결제 기한 뒤(포트원 {@code paidAt} 과 비교 — 늦은 결제)·금액 ≠ 확정 합계면 REJECT(결제 모듈이 전액 자동 취소를 넣는다). 받아들이면 예약(SCHEDULED) + 결제 시각·결제 주문 +
 *       처리 기록 + 사업자 알림. 신청 건 행은 결제 행 다음에 잠근다(결제 확정 차례).</li>
 *   <li>PG 쪽 금액·통화 불일치와 결제 주문 유효 시각이 지난 결제는 결제 모듈이 처리기 없이 자동 취소한다.</li>
 *   <li>{@code onCanceled} 는 <b>표시·알림만</b> — 신청 건 행을 잠그지 않는다(환불을 넣는 흐름이 신청 건 행 → 결제 행 차례라 교착이 된다). 자동 취소(늦은 결제·도메인 거절)면 사업자 안내만,
 *       환불 취소면 그 환불 줄에 완료 시각.</li>
 * </ul>
 */
@Component
public class BizApplicationPaymentHandler implements PaymentOrderHandler {

    public static final String REJECT_NOT_FOUND = "BIZ_APPLICATION_NOT_FOUND";
    public static final String REJECT_NOT_AWAITING = "BIZ_APPLICATION_NOT_AWAITING_PAYMENT";
    public static final String REJECT_NOT_ONLINE = "BIZ_APPLICATION_NOT_ONLINE";
    public static final String REJECT_OTHER_MEMBER = "BIZ_APPLICATION_OTHER_MEMBER";
    public static final String REJECT_LATE = "BIZ_APPLICATION_LATE_PAYMENT";
    public static final String REJECT_AMOUNT = "BIZ_APPLICATION_AMOUNT_MISMATCH";

    private final BizApplicationStore store;
    private final BizRefundStore refunds;
    private final BizApplicationOps ops;

    public BizApplicationPaymentHandler(BizApplicationStore store, BizRefundStore refunds, BizApplicationOps ops) {
        this.store = store;
        this.refunds = refunds;
        this.ops = ops;
    }

    @Override
    public PaymentOrderType orderType() {
        return PaymentOrderType.BIZ_APPLICATION;
    }

    @Override
    public PaymentDecision onPaid(PaidPayment payment) {
        Optional<AppRow> found = store.lock(payment.orderRefId());
        if (found.isEmpty()) {
            return PaymentDecision.reject(REJECT_NOT_FOUND);
        }
        AppRow app = found.get();
        if (app.status() != BizApplicationStatus.AWAITING_PAYMENT) {
            return PaymentDecision.reject(REJECT_NOT_AWAITING);
        }
        if (app.paymentMethod() != BizPaymentMethod.ONLINE) {
            return PaymentDecision.reject(REJECT_NOT_ONLINE);
        }
        if (app.userId() == null || app.userId() != payment.userId()) {
            return PaymentDecision.reject(REJECT_OTHER_MEMBER);
        }
        if (app.paymentDeadlineAt() == null || payment.paidAt().isAfter(app.paymentDeadlineAt())) {
            return PaymentDecision.reject(REJECT_LATE);
        }
        if (app.approved() == null || payment.amount() != app.approved().total()) {
            return PaymentDecision.reject(REJECT_AMOUNT);
        }
        Instant now = ops.now();
        if (!store.markPaid(app.id(), payment.paidAt(), payment.paymentOrderId(), now)) {
            return PaymentDecision.reject(REJECT_NOT_AWAITING);
        }
        ops.event(app.id(), BizEventAction.PAID, BizActorType.MEMBER, app.userId(), null, now);
        ops.event(app.id(), BizEventAction.SCHEDULED, BizActorType.SYSTEM, null, null, now);
        ops.notices().member(app, BizApplicationNotices.Kind.PAID, payment.amount(), null);
        ops.scheduled(app);
        return PaymentDecision.accept();
    }

    @Override
    public void onCanceled(CanceledPayment cancellation) {
        Instant now = ops.now();
        if (cancellation.anomaly() != null) {
            // 늦은 결제·도메인 거절의 자동 취소 — 신청 건은 그대로(이미 닫혔거나 다른 결제로 확정됐다), 사업자에게 안내만
            store.find(cancellation.orderRefId()).ifPresent(app -> {
                ops.event(app.id(), BizEventAction.AUTO_CANCELED, BizActorType.SYSTEM, null, cancellation.anomaly().name(), now);
                ops.notices().member(app, BizApplicationNotices.Kind.AUTO_CANCELED, cancellation.amount(), null);
            });
            return;
        }
        refunds.completeByCancel(cancellation.cancelId(), now).flatMap(refund -> store.find(refund.applicationId())).ifPresent(app -> {
            ops.event(app.id(), BizEventAction.REFUNDED, BizActorType.SYSTEM, null, String.valueOf(cancellation.amount()), now);
            ops.notices().member(app, BizApplicationNotices.Kind.REFUNDED, cancellation.amount(), null);
        });
    }

    /** 멈춘 환불 취소를 운영자가 직접 처리했다(콘솔 취소·계좌 송금·표시만) — 환불 줄을 완료로 */
    @Override
    public void onCancelResolvedManually(ManualCancelResolution resolution) {
        Instant now = ops.now();
        refunds.completeByCancel(resolution.cancelId(), now).flatMap(refund -> store.find(refund.applicationId()))
                .ifPresent(app -> ops.event(app.id(), BizEventAction.REFUNDED, BizActorType.OPERATOR, resolution.operatorId(),
                        String.valueOf(resolution.amount()), now));
    }
}

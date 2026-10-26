package com.community.payment.service;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.response.PaymentCompletionView;
import com.community.payment.exception.PaymentNotPaidException;
import com.community.payment.exception.PaymentOrderMismatchException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.repository.master.PaymentOrderStore;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.service.PaymentConfirmService.ConfirmResult;
import org.springframework.stereotype.Service;

/**
 * 완료 API(결정 6 — {@code POST /api/payments/{paymentId}/complete} + {@code {orderType, orderRefId}}) — 화면이 결제창을 마친 뒤 부른다.
 *
 * <ul>
 *   <li><b>그 회원·그 주문의 결제가 아니면 포트원 조회 없이 거절</b>(P24): 없거나 다른 회원의 결제 = 404 {@code PAYMENT_ORDER_NOT_FOUND}(있는지 알리지 않는다),
 *       그 회원의 다른 주문 결제 = 409 {@code PAYMENT_ORDER_MISMATCH}.</li>
 *   <li>그다음은 확정 코드 하나({@link PaymentConfirmService#confirm}). 포트원이 아직 결제 완료가 아니면 409 {@code PAYMENT_NOT_PAID}(다시 시도 가능), 포트원 장애 503.</li>
 *   <li>이미 웹훅이 확정했으면 같은 결과를 돌려준다(조건부 UPDATE 에 진 쪽). 속도 제한·멱등 키는 두지 않는다(commerce.md 12-4 — 상태를 새로 만들지 않는 확인 요청이다).</li>
 * </ul>
 * 트랜잭션 밖에서 돈다(포트원 조회가 트랜잭션 밖이어야 한다 — 확정은 확정 코드가 새 트랜잭션으로 한다).
 */
@Service
public class PaymentCompletionService {

    private final PaymentOrderStore orders;
    private final PaymentConfirmService confirm;

    public PaymentCompletionService(PaymentOrderStore orders, PaymentConfirmService confirm) {
        this.orders = orders;
        this.confirm = confirm;
    }

    public PaymentCompletionView complete(long userId, String paymentId, PaymentOrderType orderType, long orderRefId) {
        OrderRow order = orders.findByPaymentId(paymentId).filter(found -> found.userId() == userId).orElseThrow(PaymentOrderNotFoundException::new);
        if (order.orderType() != orderType || order.orderRefId() != orderRefId) {
            throw new PaymentOrderMismatchException();
        }
        ConfirmResult result = confirm.confirm(paymentId, ConfirmedVia.COMPLETE_API);
        return switch (result.outcome()) {
            case NOT_FOUND -> throw new PaymentOrderNotFoundException();
            case NOT_PAID -> throw new PaymentNotPaidException();
            case ACCEPTED, REJECTED, ALREADY_DONE -> view(result.order());
        };
    }

    static PaymentCompletionView view(OrderRow order) {
        return new PaymentCompletionView(order.paymentId(), order.orderType().name(), Long.toString(order.orderRefId()), order.status(), order.accepted(), order.anomaly(),
                order.amount(), order.paidAmount(), order.paidAt());
    }
}

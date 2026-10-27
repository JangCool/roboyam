package com.community.payment.service;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.response.PaymentResult;
import com.community.payment.exception.PaymentNotPaidException;
import com.community.payment.exception.PaymentOrderMismatchException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.repository.master.PaymentCancelStore;
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
 *   <li>응답은 {@link PaymentResult}(2a-1 화면 계약) — 확정 뒤 결제 주문을 다시 읽고, 자동 취소 줄({@code AUTO_REJECT})의 금액·상태를 싣는다.</li>
 * </ul>
 * 트랜잭션 밖에서 돈다(포트원 조회가 트랜잭션 밖이어야 한다 — 확정은 확정 코드가 새 트랜잭션으로 한다).
 */
@Service
public class PaymentCompletionService {

    private final PaymentOrderStore orders;
    private final PaymentCancelStore cancels;
    private final PaymentConfirmService confirm;

    public PaymentCompletionService(PaymentOrderStore orders, PaymentCancelStore cancels, PaymentConfirmService confirm) {
        this.orders = orders;
        this.cancels = cancels;
        this.confirm = confirm;
    }

    public PaymentResult complete(long userId, String paymentId, PaymentOrderType orderType, long orderRefId) {
        OrderRow order = orders.findByPaymentId(paymentId).filter(found -> found.userId() == userId).orElseThrow(PaymentOrderNotFoundException::new);
        if (order.orderType() != orderType || order.orderRefId() != orderRefId) {
            throw new PaymentOrderMismatchException();
        }
        ConfirmResult result = confirm.confirm(paymentId, ConfirmedVia.COMPLETE_API);
        return switch (result.outcome()) {
            case NOT_FOUND -> throw new PaymentOrderNotFoundException();
            case NOT_PAID -> throw new PaymentNotPaidException();
            case ACCEPTED, REJECTED, ALREADY_DONE -> resultOf(paymentId);
        };
    }

    /**
     * 결제 한 건의 지금 결과 — 결제 주문과 취소 줄을 다시 읽는다(확정 뒤 커밋된 자동 취소·즉시 시도의 결과까지 보이게).
     *
     * @throws PaymentOrderNotFoundException 없는 결제
     */
    public PaymentResult resultOf(String paymentId) {
        OrderRow order = orders.findByPaymentId(paymentId).orElseThrow(PaymentOrderNotFoundException::new);
        return resultOf(order);
    }

    /** 이미 읽은 결제 주문의 결과(취소 줄만 읽는다) */
    public PaymentResult resultOf(OrderRow order) {
        return PaymentResult.of(order, cancels.findByOrder(order.id()));
    }
}

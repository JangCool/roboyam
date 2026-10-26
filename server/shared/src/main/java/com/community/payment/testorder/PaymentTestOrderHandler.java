package com.community.payment.testorder;

import com.community.payment.dto.CanceledPayment;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.handler.PaymentOrderHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 결제 모듈 시험 주문({@code PAYMENT_TEST}, 결정 7)의 처리기 — 도메인 없이 결제 모듈만 끝까지 돌려 보려는 「가짜 도메인」이다. {@code roboyam.payment.test-orders=true} 인 서버에만 있다
 * (local·dev — prod 에서 켜면 기동하지 않는다, {@code PaymentConfig}).
 *
 * <ul>
 *   <li>판정은 시험 주문을 만들 때 고른다 — ACCEPT(받아들임) · REJECT(도메인 거절 → 자동 취소) · ERROR(처리기 예외 → 확정 전체가 되돌려진다).</li>
 *   <li>「현금」 장부: 받아들인 결제 금액을 더하고 그 결제의 취소를 뺀다 — 불변식 「결제 − 취소 = 도메인의 현금」을 시험이 확인한다.</li>
 *   <li>상태는 <b>이 서버의 메모리</b>다(시험 주문 표를 두지 않는다 — 계약의 표 7개). 다시 띄우면 잊고, 다른 서버(관리자 앱의 READY 정리)가 확정하면 판정은 기본 ACCEPT 다 — 시연용으로 받아들인다.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "roboyam.payment", name = "test-orders", havingValue = "true")
public class PaymentTestOrderHandler implements PaymentOrderHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentTestOrderHandler.class);

    /** 시험 주문의 판정 */
    public enum Decision {
        ACCEPT,
        REJECT,
        ERROR
    }

    /** 거절 사유 코드 */
    public static final String REJECT_REASON = "PAYMENT_TEST_REJECTED";

    private final Map<Long, Decision> decisions = new ConcurrentHashMap<>();
    private final Map<Long, Long> cash = new ConcurrentHashMap<>();
    private final Map<Long, Integer> canceledCalls = new ConcurrentHashMap<>();

    @Override
    public PaymentOrderType orderType() {
        return PaymentOrderType.PAYMENT_TEST;
    }

    /** 시험 주문의 판정을 정해 둔다(만들 때) */
    public void decide(long orderRefId, Decision decision) {
        decisions.put(orderRefId, decision == null ? Decision.ACCEPT : decision);
    }

    @Override
    public PaymentDecision onPaid(PaidPayment payment) {
        Decision decision = decisions.getOrDefault(payment.orderRefId(), Decision.ACCEPT);
        switch (decision) {
            case ERROR -> throw new IllegalStateException("시험 주문 처리기 오류(시험이 고른 판정)");
            case REJECT -> {
                log.info("시험 주문을 거절했다 orderRefId={}", payment.orderRefId());
                return PaymentDecision.reject(REJECT_REASON);
            }
            default -> {
                cash.merge(payment.orderRefId(), payment.amount(), Long::sum);
                return PaymentDecision.accept();
            }
        }
    }

    @Override
    public void onCanceled(CanceledPayment cancellation) {
        canceledCalls.merge(cancellation.cancelId(), 1, Integer::sum);
        if (cancellation.anomaly() == null) {
            cash.merge(cancellation.orderRefId(), -cancellation.amount(), Long::sum);
        }
    }

    /** 그 시험 주문의 「현금」(받아들인 결제 − 그 취소) */
    public long cashOf(long orderRefId) {
        return cash.getOrDefault(orderRefId, 0L);
    }

    /** 그 취소에 대해 {@code onCanceled} 가 불린 횟수 */
    public int canceledCallsOf(long cancelId) {
        return canceledCalls.getOrDefault(cancelId, 0);
    }
}

package com.community.payment.testorder;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.PgCancelStatus;
import com.community.payment.gateway.PaymentGatewayKind;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 결제 시험 화면({@code /api/dev/payments})의 요청·응답 모양 */
public final class PaymentTestViews {

    private PaymentTestViews() {
    }

    /**
     * 시험 주문 만들기.
     *
     * @param amount   금액(100~1,000,000원 — 시험 화면이 정한다. 실제 도메인은 서버가 계산한다)
     * @param decision 처리기 판정(없으면 ACCEPT)
     */
    public record CreateRequest(long amount, PaymentTestOrderHandler.Decision decision) {
    }

    /** 만든 시험 주문 — 화면은 이 값으로 결제창(가짜 또는 포트원)을 연다 */
    public record CreatedView(String orderRefId, String paymentId, String orderName, long amount, String currency, Instant expiresAt, PaymentGatewayKind provider,
                              String storeId, String channelKey) {
    }

    /**
     * 가짜 결제창의 결과.
     *
     * @param result PAID(결제함) · FAILED(실패)
     * @param amount 결제했다고 할 금액(없으면 주문 금액 — 금액 불일치를 흉내 내려면 다른 값)
     */
    public record FakePayRequest(@NotNull FakeResult result, Long amount) {
    }

    /** 가짜 결제창의 결과 */
    public enum FakeResult {
        PAID,
        FAILED
    }

    /** 시험 주문의 지금 상태(결제 주문·취소·처리기의 현금) */
    public record StatusView(String paymentId, String orderRefId, PaymentStatus status, PaymentAnomaly anomaly, long amount, Long paidAmount, long canceledAmount,
                             long handlerCash, List<CancelView> cancels) {
    }

    /** 취소 한 줄 */
    public record CancelView(String id, CancelKind kind, long amount, CancelStatus status, PgCancelStatus pgStatus, int attempts) {
    }
}

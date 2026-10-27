package com.community.payment.testorder;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.PgCancelStatus;
import com.community.payment.dto.response.PaymentCheckoutView;
import com.community.payment.dto.response.PaymentResult;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 결제 시험 화면({@code /api/dev/payments})의 요청·응답 모양 — 2a-1 화면 계약(user-web {@code usePaymentApi.types.ts})이 정본이다 */
public final class PaymentTestViews {

    private PaymentTestViews() {
    }

    /**
     * 시험 주문 만들기({@code POST /api/dev/payments}).
     *
     * @param amount       금액(100~1,000,000원 — 시험 화면이 정한다. 실제 도메인은 서버가 계산한다)
     * @param validMinutes 결제 유효 시간(1~60분) — 결제 주문의 {@code expires_at} = 만든 시각 + 이 시간
     * @param decision     처리기 판정(화면에 없는 선택 칸 — 없으면 ACCEPT. 도메인 거절·처리기 예외를 시험할 때만)
     */
    public record CreateRequest(Long amount, Integer validMinutes, PaymentTestOrderHandler.Decision decision) {
    }

    /** 만든 시험 주문 — 화면은 {@code checkout} 그대로 결제창(가짜 또는 포트원)을 연다 */
    public record CreatedView(String orderType, String orderRefId, Instant expiresAt, PaymentCheckoutView checkout) {
    }

    /**
     * 가짜 결제창의 결과({@code POST /api/dev/payments/{paymentId}/fake-checkout}).
     *
     * @param outcome PAID(결제함) · FAILED(실패)
     * @param amount  결제했다고 할 금액(1원 이상, 없으면 주문 금액 — 주문 금액과 다르면 금액 불일치 시험). FAILED 면 보지 않는다
     * @param late    true 면 결제 시각 = 유효 시각 + 1분(늦은 결제 시험, 결정 4 — 없으면 false). FAILED 면 보지 않는다
     */
    public record FakeCheckoutRequest(@NotNull FakeOutcome outcome, Long amount, Boolean late) {
    }

    /** 가짜 결제창의 결과 */
    public enum FakeOutcome {
        PAID,
        FAILED
    }

    /**
     * 시험 결제 다시 읽기({@code GET /api/dev/payments/{paymentId}}) — 칸은 {@link PaymentResult} 그대로 펼치고, 시험용으로 처리기의 현금과 취소 줄 전부를 더 싣는다.
     *
     * @param handlerCash 시험 처리기의 「현금」(받아들인 결제 − 그 취소, 이 서버 메모리)
     * @param cancels     이 결제의 취소 줄 전부
     */
    public record StatusView(@JsonUnwrapped PaymentResult result, long handlerCash, List<CancelView> cancels) {
    }

    /** 취소 한 줄 */
    public record CancelView(String id, CancelKind kind, long amount, CancelStatus status, PgCancelStatus pgStatus, int attempts) {
    }
}

package com.community.payment.gateway;

import com.community.payment.dto.PgCancelStatus;
import java.time.Instant;

/**
 * 포트원 결제 건의 취소 내역 한 줄({@code PaymentCancellation}).
 *
 * @param id          포트원 취소 내역 아이디({@code id}) — 웹훅 {@code cancellationId} 와 같은 값, 우리 {@code payment_cancels.pg_cancel_id}
 * @param status      상태(REQUESTED·SUCCEEDED·FAILED)
 * @param amount      취소 금액({@code totalAmount})
 * @param reason      취소 사유(우리가 보낸 값 — 우리 취소 표시 {@code rc:<취소 ID>} 를 찾는다)
 * @param cancelledAt 취소 시점(없을 수 있다)
 */
public record PgCancellation(String id, PgCancelStatus status, long amount, String reason, Instant cancelledAt) {
}

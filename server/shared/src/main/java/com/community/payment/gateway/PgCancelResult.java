package com.community.payment.gateway;

import com.community.payment.dto.PgCancelStatus;

/**
 * 포트원 취소 요청({@code POST /payments/{paymentId}/cancel})의 결과.
 *
 * @param status         취소 내역 상태 — 200 이면 응답의 {@code cancellation.status}, 포트원이 거절(4xx)했으면 FAILED
 * @param cancellationId 포트원 취소 내역 아이디(200 일 때만)
 * @param errorType      거절의 포트원 오류 {@code type}(예 {@code CANCELLABLE_AMOUNT_CONSISTENCY_BROKEN}·{@code PAYMENT_ALREADY_CANCELLED}) — 가린 오류 한 줄용
 */
public record PgCancelResult(PgCancelStatus status, String cancellationId, String errorType) {

    public static PgCancelResult succeeded(String cancellationId) {
        return new PgCancelResult(PgCancelStatus.SUCCEEDED, cancellationId, null);
    }

    public static PgCancelResult requested(String cancellationId) {
        return new PgCancelResult(PgCancelStatus.REQUESTED, cancellationId, null);
    }

    public static PgCancelResult failed(String cancellationId, String errorType) {
        return new PgCancelResult(PgCancelStatus.FAILED, cancellationId, errorType);
    }

    public boolean succeeded() {
        return status == PgCancelStatus.SUCCEEDED;
    }
}

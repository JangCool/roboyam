package com.community.payment.dto;

import java.time.Instant;

/**
 * 처리기에 넘기는 「운영자가 직접 처리한 취소」(공구 계약 결정 16·74).
 *
 * @param method     처리 방법 — null 이면 표시만(실제 처리 방법을 적지 않았다)
 * @param pgCancelId 콘솔 취소면 포트원 취소 ID
 */
public record ManualCancelResolution(long paymentOrderId, PaymentOrderType orderType, long orderRefId, long userId, long cancelId, CancelKind kind, long amount,
                                     ManualCancelMethod method, String pgCancelId, long operatorId, Instant resolvedAt) {
}

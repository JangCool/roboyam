package com.community.payment.dto.response;

import java.time.Instant;

/**
 * 웹훅 수신 기록 한 줄 — 본문은 저장하지 않는다(받은 시각·이벤트·결제 번호·결과·HTTP·사유 코드·webhook-id 만).
 *
 * @param paymentOrderId 우리 결제 주문이면 그 번호(서랍 열기)
 * @param result         PROCESSED · IGNORED · REJECTED · RETRY
 */
public record PaymentWebhookRowView(String id, String webhookId, String eventType, String paymentId, String paymentOrderId, String result, int httpStatus,
                                    String errorCode, Instant receivedAt) {
}

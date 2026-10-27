package com.community.payment.dto.response;

import java.time.Instant;

/**
 * 결제 이상 한 줄(확정한 목업 가정 4) — 이상 결제 주문 하나({@code ORDER:<주문 ID>}) 또는 이상이 아닌 결제의 멈췄던 취소 하나({@code CANCEL:<취소 ID>}).
 *
 * @param kind     이상 종류(AMOUNT_MISMATCH·DOMAIN_REJECTED·LATE_PAYMENT) 또는 CANCEL_GAVE_UP
 * @param cancel   이상 결제의 자동 취소 줄 · 멈췄던 취소 줄
 * @param resolved 처리됨 — 그 취소가 SUCCEEDED·MANUAL
 */
public record PaymentAnomalyRowView(String key, String kind, PaymentOrderRowView order, PaymentCancelView cancel, Instant detectedAt, boolean resolved) {
}

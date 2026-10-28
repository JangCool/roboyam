package com.community.payment.gateway;

import java.time.Instant;

/**
 * 에스크로 배송 정보(공구 계약 결정 4·14·64 — 포트원 {@code POST·PATCH /payments/{paymentId}/escrow/logistics}).
 *
 * @param carrierCode 택배사 코드(우리 코드 = 포트원 택배사 코드 — CJ·HANJIN·LOTTE·EPOST·LOGEN·KDEXP)
 * @param trackingNo  송장 번호(숫자·영문 6~30자로 정리된 값)
 * @param sentAt      발송 시각
 */
public record EscrowLogistics(String carrierCode, String trackingNo, Instant sentAt) {
}

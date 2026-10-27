package com.community.payment.dto.request;

import com.community.payment.dto.PaymentOrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 완료 API 본문(결정 6) — 이 결제가 어느 주문의 것인지 화면이 알려 준다(서버가 그 회원·그 주문의 결제인지 맞춰 본다, P24).
 *
 * @param orderRefId 도메인 주문 ID(문자열 — Snowflake)
 */
public record PaymentCompleteRequest(@NotNull PaymentOrderType orderType, @NotNull @Pattern(regexp = "[0-9]{1,19}") String orderRefId) {
}

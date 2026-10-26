package com.community.pointshop.dto.request;

/**
 * {@code POST /api/point-shop/purchases}(user-web {@code PointShopPurchaseRequest}) — 헤더 {@code Idempotency-Key} 필수. 가격은 받지 않는다(서버가 품목에서 읽는다).
 * 검사 순서가 계약이라(자격 403 → 고지 동의 400 → 품목 404 …) Bean Validation 없이 서비스가 본다.
 *
 * @param itemId            품목 ID(문자열 Snowflake) — 없거나 형식이 틀리면 없는 품목(404)
 * @param agreeRefundNotice 환불 고지 동의 — true 가 아니면 {@code 400 VALIDATION fields.agreeRefundNotice}
 */
public record PointShopPurchaseRequest(String itemId, Boolean agreeRefundNotice) {
}

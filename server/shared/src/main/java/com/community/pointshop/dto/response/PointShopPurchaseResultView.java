package com.community.pointshop.dto.response;

/** {@code POST /api/point-shop/purchases} — 산 뒤 내 포인트와 산 뒤의 품목(재고·내 보유가 바뀐 것) */
public record PointShopPurchaseResultView(String purchaseId, String userItemId, long balance, PointShopItemView item) {
}

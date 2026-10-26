package com.community.pointshop.dto.response;

import java.util.List;

/** {@code GET /api/admin/point-shop/purchases} — 최신 구매가 위 */
public record PointShopPurchasePageView(List<PointShopPurchaseRowView> items, long total, int page, int size) {
}

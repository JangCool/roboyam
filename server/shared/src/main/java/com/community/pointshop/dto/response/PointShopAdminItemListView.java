package com.community.pointshop.dto.response;

import java.util.List;

/** {@code GET /api/admin/point-shop/items?kind=&excludeKind=} — 순서 → ID 차례 */
public record PointShopAdminItemListView(List<PointShopAdminItemView> items) {
}

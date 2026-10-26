package com.community.pointshop.dto.response;

import java.util.List;

/** {@code GET /api/admin/point-shop/avatars/summary} — 카테고리는 카탈로그 차례(무료 2 + 유료 12) */
public record PointShopAvatarSummaryView(List<PointShopAvatarCategorySummaryView> categories, PointShopAvatarTotalsView totals) {
}

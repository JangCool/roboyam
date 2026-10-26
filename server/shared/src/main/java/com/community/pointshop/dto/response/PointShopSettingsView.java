package com.community.pointshop.dto.response;

import java.time.Instant;

/**
 * {@code GET·PUT /api/admin/point-shop/settings}.
 *
 * @param defaultRefundDays 처음 값(7) — 「기본값 N」 표시
 * @param updatedAt         마지막 저장 시각(저장한 적 없으면 null)
 * @param updatedBy         마지막으로 저장한 운영자 이름(없거나 지워졌으면 null)
 */
public record PointShopSettingsView(int refundDays, int defaultRefundDays, Instant updatedAt, String updatedBy) {
}

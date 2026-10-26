package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopUsageType;
import java.time.Instant;

/**
 * 관리자 품목 한 줄 — {@code point_shop_items} 칸 전부 + 판 수 + 만든·고친 운영자 이름(admin-web {@code PointShopAdminItem}).
 *
 * @param category  아바타만 — 카탈로그 카테고리 ID(이름은 화면 i18n)
 * @param soldCount 판 수(환불한 구매는 빼고)
 * @param createdBy 만든 운영자 이름 — 시스템(마이그레이션)이면 null
 */
public record PointShopAdminItemView(String id, String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description,
                                     String descriptionEn, String color, String borderStyle, String avatarId, String category, int pricePoint, Integer durationDays,
                                     Integer stock, Instant saleStartAt, Instant saleEndAt, boolean active, int sortOrder, int soldCount, Instant createdAt,
                                     String createdBy, Instant updatedAt, String updatedBy) {
}

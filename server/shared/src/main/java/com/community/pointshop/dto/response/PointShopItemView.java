package com.community.pointshop.dto.response;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopSaleState;
import com.community.pointshop.entity.PointShopUsageType;
import java.time.Instant;

/**
 * 회원이 보는 품목 하나 — {@code GET /api/point-shop/items} 의 한 줄이자 {@code GET /api/point-shop/items/{id}} 의 응답(user-web {@code PointShopItemView}).
 * {@code price} 는 지금 가격(구매 때 서버가 품목에서 다시 읽는다), {@code stock} 은 남은 재고(null = 무제한), {@code saleState} 는 서버 계산.
 */
public record PointShopItemView(String id, String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description,
                                String descriptionEn, String color, String borderStyle, String avatarId, String avatarUrl, int price, Integer durationDays, Integer stock,
                                Instant saleStartAt, Instant saleEndAt, PointShopSaleState saleState, PointShopItemMineView mine) {
}

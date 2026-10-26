package com.community.pointshop.dto.response;

/** 아바타 전체 요약 */
public record PointShopAvatarTotalsView(int total, int freeCount, int paidCount, int forSale, int stopped, Integer minPrice, Integer maxPrice) {
}

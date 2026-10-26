package com.community.pointshop.dto.response;

/** 아바타 카테고리 하나의 요약 — 무료 카테고리는 품목이 없어 판매 0·가격 범위 null */
public record PointShopAvatarCategorySummaryView(String category, boolean free, int total, int forSale, int stopped, Integer minPrice, Integer maxPrice) {
}

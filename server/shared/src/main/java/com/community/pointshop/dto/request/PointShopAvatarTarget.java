package com.community.pointshop.dto.request;

import java.util.List;

/**
 * 아바타 묶음 범위 — {@code CATEGORIES} 면 {@code categories}(유료 카테고리 1개 이상)만, {@code ITEMS} 면 {@code itemIds}(아바타 품목 1~400개)만 보낸다(다른 범위 칸을 같이 보내면 400).
 * 검사는 서비스가 한다({@code fields.scope|categories|itemIds}).
 */
public record PointShopAvatarTarget(PointShopAvatarScope scope, List<String> categories, List<String> itemIds) {
}

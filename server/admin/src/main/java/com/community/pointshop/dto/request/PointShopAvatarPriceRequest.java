package com.community.pointshop.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/point-shop/avatars/price} — 범위({@code ALL}|{@code CATEGORIES}|{@code ITEMS}) + 가격(1~100,000). {@code scope} 는 글자로 받는다 — 모르는 값도
 * {@code 400 fields.scope} 로 다른 칸 오류와 같이 돌려주려고(enum 으로 받으면 본문을 못 읽어 {@code MALFORMED_REQUEST} 가 된다).
 */
public record PointShopAvatarPriceRequest(String scope, List<String> categories, List<String> itemIds, Integer price) {
}

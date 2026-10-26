package com.community.pointshop.dto.request;

import java.util.List;

/** {@code POST /api/admin/point-shop/avatars/sale} — 범위 + 판매 켜기(true)·끄기(false). {@code scope} 를 글자로 받는 이유는 {@link PointShopAvatarPriceRequest} 와 같다 */
public record PointShopAvatarSaleRequest(String scope, List<String> categories, List<String> itemIds, Boolean active) {
}

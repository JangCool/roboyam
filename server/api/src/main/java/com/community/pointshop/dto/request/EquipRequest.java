package com.community.pointshop.dto.request;

/**
 * {@code PUT /api/me/equipped/{slot}}(user-web {@code EquipRequest}).
 *
 * @param userItemId 장착할 내 보유 줄 ID(문자열 Snowflake) — 없거나 형식이 틀리면 {@code 404 USER_ITEM_NOT_FOUND}
 */
public record EquipRequest(String userItemId) {
}

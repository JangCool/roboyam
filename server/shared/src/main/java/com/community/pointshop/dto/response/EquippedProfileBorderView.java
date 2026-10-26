package com.community.pointshop.dto.response;

import java.time.Instant;

/** 장착한 프로필 테두리 — 팔레트 키는 품목의 지금 값 */
public record EquippedProfileBorderView(String userItemId, String itemId, String borderStyle, Instant expiresAt) {
}

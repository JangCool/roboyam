package com.community.pointshop.dto.response;

import java.time.Instant;

/** 장착한 닉네임 색 — 색은 품목의 지금 값 */
public record EquippedNicknameColorView(String userItemId, String itemId, String color, Instant expiresAt) {
}

package com.community.profile.dto.request;

/**
 * {@code PUT /api/me/profile/avatar} — 고를 아바타의 카탈로그 ID(user-web {@code AvatarSelectRequest}). 형식은 서비스가 본다({@code 400 VALIDATION fields.avatarId}).
 */
public record AvatarSelectRequest(String avatarId) {
}

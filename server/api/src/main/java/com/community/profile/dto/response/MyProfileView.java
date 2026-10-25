package com.community.profile.dto.response;

import java.time.Instant;

/**
 * {@code GET}·{@code PUT /api/me/profile} 응답(user-web {@code MyProfile}).
 *
 * @param bio      한 줄 소개 — 없으면 빈 글자
 * @param joinedAt 가입 시각
 */
public record MyProfileView(String nickname, String bio, Instant joinedAt, NicknameChangeView nicknameChange) {
}

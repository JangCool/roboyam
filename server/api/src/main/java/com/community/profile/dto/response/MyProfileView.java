package com.community.profile.dto.response;

import java.time.Instant;

/**
 * {@code GET}·{@code PUT /api/me/profile}·{@code PUT /api/me/profile/avatar} 응답(user-web {@code MyProfile}).
 *
 * @param bio       한 줄 소개 — 없으면 빈 글자
 * @param joinedAt  가입 시각
 * @param avatarUrl 프로필 아바타 그림 주소 — 늘 채운다(고르지 않았거나 카탈로그에 없으면 {@code basic-default}). 커머스 1-2 결정 14
 * @param avatarStillUrl 움직이는 아바타의 정지 그림 — 움직이는 것만, 정지 아바타는 null(commerce-motion-avatar-contract)
 */
public record MyProfileView(String nickname, String bio, Instant joinedAt, NicknameChangeView nicknameChange, String avatarUrl, String avatarStillUrl) {
}

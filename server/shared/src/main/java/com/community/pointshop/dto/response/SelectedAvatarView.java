package com.community.pointshop.dto.response;

/** 아바타를 고른 결과 — api 가 {@code MyProfile} 의 {@code avatarUrl}·{@code avatarStillUrl} 로 싣는다(정지 그림은 움직이는 아바타만) */
public record SelectedAvatarView(String avatarId, String avatarUrl, String avatarStillUrl, boolean animated) {
}

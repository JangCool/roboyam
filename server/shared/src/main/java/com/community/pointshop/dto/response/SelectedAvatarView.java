package com.community.pointshop.dto.response;

/** 아바타를 고른 결과 — api 가 {@code MyProfile} 의 {@code avatarUrl} 로 싣는다 */
public record SelectedAvatarView(String avatarId, String avatarUrl) {
}

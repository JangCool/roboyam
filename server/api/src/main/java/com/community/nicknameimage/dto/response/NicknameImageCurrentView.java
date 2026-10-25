package com.community.nicknameimage.dto.response;

/** 적용 중인 그림 — user-web {@code NicknameImageView}. {@code id} 는 적용한 신청 ID, {@code still} 은 움직이는 GIF 의 정지 그림(그 밖 null) */
public record NicknameImageCurrentView(String id, String url, String still) {
}

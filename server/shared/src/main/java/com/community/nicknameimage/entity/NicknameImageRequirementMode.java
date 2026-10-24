package com.community.nicknameimage.entity;

/** 닉네임 그림 신청 조건 방식 — {@code nickname_image_settings.mode}. 등급 기준({@link #GRADE}) · 사용자 정의({@link #CUSTOM}) */
public enum NicknameImageRequirementMode {
    /** 최소 등급 하나 + 이메일 인증(늘 필요) */
    GRADE,
    /** 최소 등급(없음 가능) · 가입 후 일수 · 글+댓글 수 · 보유 포인트 · 이메일 인증(끌 수 있음)을 모두 채워야 한다 */
    CUSTOM
}

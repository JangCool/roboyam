package com.community.profile.dto.response;

/** 내 알림 수신 설정 — 관리자 알림을 이메일로 받을지(처음 값 켜짐) */
public record MyNotificationResponse(boolean emailEnabled) {
}

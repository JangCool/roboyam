package com.community.profile.dto.request;

/** 내 알림 수신 설정 저장 — {@code emailEnabled} 가 없으면(null) 400 {@code fields.emailEnabled} */
public record MyNotificationRequest(Boolean emailEnabled) {
}

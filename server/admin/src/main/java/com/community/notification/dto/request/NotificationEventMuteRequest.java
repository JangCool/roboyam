package com.community.notification.dto.request;

/** 내 알림 이벤트 끄기/켜기 — {@code muted} 가 없으면(null) 400 {@code fields.muted} */
public record NotificationEventMuteRequest(Boolean muted) {
}

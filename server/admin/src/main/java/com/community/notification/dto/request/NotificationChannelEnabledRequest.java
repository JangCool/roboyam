package com.community.notification.dto.request;

/** 사용 켜기/끄기 — {@code enabled} 가 없으면 400 {@code fields.enabled} */
public record NotificationChannelEnabledRequest(Boolean enabled) {
}

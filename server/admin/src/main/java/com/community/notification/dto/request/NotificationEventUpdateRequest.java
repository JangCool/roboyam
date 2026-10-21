package com.community.notification.dto.request;

/** 이벤트 관리의 사용 여부·이메일 전달 저장 — 두 칸 모두 있어야 한다(없으면 400 {@code fields.enabled}·{@code fields.emailEnabled}) */
public record NotificationEventUpdateRequest(Boolean enabled, Boolean emailEnabled) {
}

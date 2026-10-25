package com.community.membernotification.dto.request;

/** 알림 설정 저장의 한 줄 — 회원이 끌 수 있는 이벤트만 보낸다. 빠진 칸은 서비스가 {@code fields.events[i].*} 로 거절한다 */
public record MyNotificationSettingInput(String code, Boolean inApp, Boolean email) {
}

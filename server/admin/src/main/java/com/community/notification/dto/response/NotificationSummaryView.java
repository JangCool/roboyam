package com.community.notification.dto.response;

/** 폴링용 요약 — 안 읽은 수와 가장 최근 알림의 id(읽음 여부와 상관없이, 없으면 null). 화면은 {@code latestId} 가 바뀌면 새 알림이 도착한 것으로 보고 목록을 받는다 */
public record NotificationSummaryView(long unread, String latestId) {
}

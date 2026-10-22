package com.community.membernotification.dto.response;

/** {@code GET /api/notifications/unread-count} — 안 읽은 알림 수(상한 100) */
public record UnreadCountView(int count) {
}

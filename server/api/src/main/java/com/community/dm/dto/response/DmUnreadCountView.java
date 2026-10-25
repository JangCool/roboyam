package com.community.dm.dto.response;

/** {@code GET /api/dm/unread-count} — 안 읽은 쪽지가 있는 대화 수(상한 100) */
public record DmUnreadCountView(int count) {
}

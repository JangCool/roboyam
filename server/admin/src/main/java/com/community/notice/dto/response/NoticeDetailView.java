package com.community.notice.dto.response;

/** {@code GET /api/admin/notices/{id}} */
public record NoticeDetailView(NoticeItemView notice, NoticeContentView content) {
}

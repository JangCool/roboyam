package com.community.notice.dto.response;

import java.util.List;

/** {@code GET /api/admin/notices} — 고정 글 먼저(최근 고정이 위), 그다음 최신. {@code page} 는 0부터 */
public record NoticeListView(long total, int page, int size, List<NoticeItemView> items) {
}

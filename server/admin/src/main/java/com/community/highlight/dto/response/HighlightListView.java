package com.community.highlight.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/highlights?place=} — 한 곳의 목록.
 *
 * @param place        놓일 곳 키(보내지 않았으면 home)
 *
 * @param visibleCount 상태가 SHOWING 인 수
 * @param total        전체 수
 */
public record HighlightListView(String place, int visibleCount, int total, List<HighlightItemView> items) {
}

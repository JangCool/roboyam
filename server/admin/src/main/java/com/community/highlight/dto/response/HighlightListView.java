package com.community.highlight.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/highlights}.
 *
 * @param visibleCount 상태가 SHOWING 인 수
 * @param total        전체 수
 */
public record HighlightListView(int visibleCount, int total, List<HighlightItemView> items) {
}

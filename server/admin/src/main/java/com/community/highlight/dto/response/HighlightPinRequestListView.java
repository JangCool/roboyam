package com.community.highlight.dto.response;

import java.util.List;

/** {@code GET /api/admin/highlights/{id}/pin-requests} — 대기 중인 고정 요청(오래된 것부터, 지우거나 숨긴 의견의 요청 제외) */
public record HighlightPinRequestListView(String highlightId, String postId, List<HighlightPinRequestView> items) {
}

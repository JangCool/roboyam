package com.community.debate.dto.response;

import java.util.List;

/** {@code GET /api/admin/debates/{postId}/pin-requests} — 대기 중인 고정 요청(오래된 것부터, 지우거나 숨긴 의견의 요청 제외). {@code highlightId} 는 없으면 null */
public record DebatePinRequestListView(String highlightId, String postId, List<DebatePinRequestView> items) {
}

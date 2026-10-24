package com.community.debate.dto.response;

import java.util.List;

/** {@code GET /api/posts/{postId}/pin-requests} — 대기 중인 요청(오래된 것부터, 내 요청·지우거나 숨긴 의견 제외) */
public record PinRequestListView(List<PinRequestReviewItemView> items) {
}

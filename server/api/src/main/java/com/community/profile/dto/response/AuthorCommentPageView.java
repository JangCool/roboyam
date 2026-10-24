package com.community.profile.dto.response;

import java.util.List;

/** {@code GET /api/users/{id}/comments} — 최신이 위, {@code nextCursor} 를 다음 요청의 {@code cursor} 로 */
public record AuthorCommentPageView(List<AuthorCommentRowView> items, boolean hasNext, String nextCursor) {
}

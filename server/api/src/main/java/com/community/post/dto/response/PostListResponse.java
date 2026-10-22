package com.community.post.dto.response;

import java.util.List;

/**
 * {@code GET /api/boards/{slug}/posts}(user-web {@code PostListResponse}).
 *
 * @param pinned 고정 공지 — 첫 쪽이고 말머리·검색어가 없을 때만, 최대 유효 {@code pinnedMax} 개
 * @param page   1부터
 */
public record PostListResponse(List<PostRowView> pinned, List<PostRowView> items, int page, int totalPages, long totalCount) {
}

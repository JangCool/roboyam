package com.community.post.dto.response;

import java.util.List;

/**
 * 커서로 이어 받는 게시판이 섞인 목록 한 쪽(user-web {@code PostFeedPage}).
 *
 * @param nextCursor 다음 요청의 {@code cursor}(마지막 글 ID) — 더 없으면 null
 */
public record PostFeedPageView(List<PostFeedRowView> items, boolean hasNext, String nextCursor) {
}

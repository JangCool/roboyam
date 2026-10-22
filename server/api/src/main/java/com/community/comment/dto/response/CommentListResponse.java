package com.community.comment.dto.response;

import java.util.List;

/**
 * {@code GET /api/posts/{id}/comments}(user-web {@code CommentListResponse}).
 *
 * @param best       베스트 댓글 — 추천 수가 기본 설정 {@code bestCommentMinLikes} 이상인 보이는 댓글, 추천 많은 순 최대 3개
 * @param totalCount 보이는 댓글 수(지운 것·숨긴 것 제외)
 */
public record CommentListResponse(List<CommentThreadView> threads, List<CommentView> best, int totalCount) {
}

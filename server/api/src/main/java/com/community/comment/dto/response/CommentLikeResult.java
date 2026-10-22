package com.community.comment.dto.response;

/** {@code POST /api/comments/{id}/like} — 새 추천 수 */
public record CommentLikeResult(int likeCount, boolean liked) {
}

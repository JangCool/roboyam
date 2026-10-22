package com.community.post.dto.response;

/** {@code POST /api/posts/{id}/like} — 새 추천 수(추천은 한 번만, 취소 없음) */
public record PostLikeResult(int likeCount, boolean liked) {
}

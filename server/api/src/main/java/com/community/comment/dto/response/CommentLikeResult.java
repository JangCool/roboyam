package com.community.comment.dto.response;

/**
 * 댓글 추천·비추천({@code POST /api/comments/{id}/like} · {@code POST|DELETE /api/comments/{id}/dislike}) 응답 — 누른 뒤의 수와 내 상태.
 * 추천과 비추천은 둘 중 하나만이라 하나를 누르면 다른 하나가 풀린 값이 함께 온다.
 *
 * @param dislikeCount 비추천 수 — 게시판의 비추천이 꺼져 있으면 null
 * @param disliked     내가 비추천했는가 — 게시판의 비추천이 꺼져 있으면 null
 */
public record CommentLikeResult(int likeCount, boolean liked, Integer dislikeCount, Boolean disliked) {
}

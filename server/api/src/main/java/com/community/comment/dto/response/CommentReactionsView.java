package com.community.comment.dto.response;

/**
 * 이 게시판의 댓글 추천·비추천 표시(게시판 정책 유효값 — 게시판 값 ?? 기본 설정). 댓글 목록 응답의 {@code reactions}.
 *
 * @param dislikeEnabled 비추천 단추를 보이고 받는가({@code commentDislikeEnabled}, 기본 false) — false 면 댓글의 {@code dislikeCount}·{@code disliked} 가 null
 * @param likeLabel      추천 단추 이름(1~10자) — null 이면 화면 기본 이름(「추천」)
 * @param dislikeLabel   비추천 단추 이름(1~10자) — null 이면 화면 기본 이름(「비추천」)
 */
public record CommentReactionsView(boolean dislikeEnabled, String likeLabel, String dislikeLabel) {
}

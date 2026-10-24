package com.community.point.dto.response;

/**
 * 내역 한 줄이 가리키는 글 — 글·댓글 사유만. 글이 지워졌거나 숨겨졌거나 꺼진 게시판이면 {@code available=false}, {@code title=null}.
 * 댓글 사유면 그 댓글이 달린 글이다(댓글을 찾지 못하면 {@code postId} 도 null).
 */
public record MyPointLedgerTargetView(String postId, String title, boolean available) {
}

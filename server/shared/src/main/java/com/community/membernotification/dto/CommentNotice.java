package com.community.membernotification.dto;

/**
 * 댓글이 저장됐다 — 회원 알림({@code COMMENT}·{@code REPLY}) 판정에 필요한 값.
 *
 * @param postAuthorId       글쓴 회원 — 비회원 글이면 null
 * @param commenterId        댓글 쓴 회원 — 비회원이면 null
 * @param commenterGuestName 비회원 댓글의 이름(가린 뒤의 값) — 회원이면 null
 * @param replyTargetAuthorId 답글이면 답한 댓글({@code replyToId}, 없으면 원댓글)의 작성 회원 — 원댓글이거나 그 댓글이 비회원 것이면 null
 * @param reply              답글인가
 */
public record CommentNotice(long postId, Long postAuthorId, long commentId, Long commenterId, String commenterGuestName, Long replyTargetAuthorId, boolean reply) {
}

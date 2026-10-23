package com.community.membernotification.dto;

/**
 * 댓글이 저장됐다 — 회원 알림({@code COMMENT}·{@code REPLY}) 판정과 회원 알림 메일 문구에 필요한 값.
 *
 * @param postAuthorId       글쓴 회원 — 비회원 글이면 null
 * @param commenterId        댓글 쓴 회원 — 비회원이면 null
 * @param commenterGuestName 비회원 댓글의 이름(가린 뒤의 값) — 회원이면 null
 * @param replyTargetAuthorId 답글이면 답한 댓글({@code replyToId}, 없으면 원댓글)의 작성 회원 — 원댓글이거나 그 댓글이 비회원 것이면 null
 * @param reply              답글인가
 * @param postTitle          글 제목(메일 문구 변수 {@code postTitle})
 * @param excerpt            댓글 본문 평문(가린 뒤의 값 — 메일 문구 변수 {@code excerpt}, 발행기가 앞부분만 자른다)
 */
public record CommentNotice(long postId, Long postAuthorId, long commentId, Long commenterId, String commenterGuestName, Long replyTargetAuthorId, boolean reply,
                            String postTitle, String excerpt) {
}

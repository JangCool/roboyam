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
 * @param anonymous          익명 댓글(V184) — 알림 행에 일으킨 회원을 남기지 않고(읽을 때 「익명」), 메일 이름도 「익명」(번호 없이 — anonymous-board-contract 「알림」).
 *                           본인 글·댓글에 단 것인지는 {@code commenterId} 로 그대로 가린다
 */
public record CommentNotice(long postId, Long postAuthorId, long commentId, Long commenterId, String commenterGuestName, Long replyTargetAuthorId, boolean reply,
                            String postTitle, String excerpt, boolean anonymous) {

    /** 익명이 아닌 댓글 */
    public CommentNotice(long postId, Long postAuthorId, long commentId, Long commenterId, String commenterGuestName, Long replyTargetAuthorId, boolean reply,
                         String postTitle, String excerpt) {
        this(postId, postAuthorId, commentId, commenterId, commenterGuestName, replyTargetAuthorId, reply, postTitle, excerpt, false);
    }
}

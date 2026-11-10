package com.community.boardstaff.dto.request;

/**
 * {@code POST /api/board-staff/sanctions}(user-web {@code BoardSanctionIssueRequest}) — 검사는 서비스가 한다. {@code reasonText} 0~200자(OTHER 면 5자 이상),
 * {@code postId}·{@code commentId} 계기(선택 — 댓글이면 글과 함께). 익명 글·댓글(V184)이면 {@code userId} 대신 {@code anonymousPostId} 또는 {@code anonymousCommentId} —
 * 서버가 그 작성자를 대상으로(계기도 그 글·댓글), 익명이 아니면 409 {@code NOT_ANONYMOUS}, 셋 중 둘 이상이면 400(anonymous-board-contract 「API 계약」).
 */
public record BoardSanctionIssueRequest(String boardId, String userId, String kind, String reasonCode, String reasonText, String postId, String commentId,
                                        String anonymousPostId, String anonymousCommentId) {
}

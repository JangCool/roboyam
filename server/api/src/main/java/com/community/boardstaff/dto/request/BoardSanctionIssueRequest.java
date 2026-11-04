package com.community.boardstaff.dto.request;

/**
 * {@code POST /api/board-staff/sanctions}(user-web {@code BoardSanctionIssueRequest}) — 검사는 서비스가 한다. {@code reasonText} 0~200자(OTHER 면 5자 이상),
 * {@code postId}·{@code commentId} 계기(선택 — 댓글이면 글과 함께).
 */
public record BoardSanctionIssueRequest(String boardId, String userId, String kind, String reasonCode, String reasonText, String postId, String commentId) {
}

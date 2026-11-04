package com.community.boardstaff.dto.request;

/** {@code POST /api/board-staff/comments/{commentId}/delete} — {@code postId} 필수(댓글 표 해시 파티션 — 그 글의 댓글이 아니면 404 COMMENT_NOT_FOUND) */
public record BoardStaffCommentDeleteRequest(String postId, String reasonCode) {
}

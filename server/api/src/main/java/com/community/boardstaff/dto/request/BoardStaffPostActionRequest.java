package com.community.boardstaff.dto.request;

/** {@code POST /api/board-staff/posts/{postId}/hide|delete} — 사유 코드 SPAM · ABUSE · INVESTMENT · OFF_TOPIC(정책 3, 자유 입력 없음) */
public record BoardStaffPostActionRequest(String reasonCode) {
}

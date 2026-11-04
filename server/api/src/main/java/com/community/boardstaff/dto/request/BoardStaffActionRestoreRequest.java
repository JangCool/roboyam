package com.community.boardstaff.dto.request;

/** {@code POST /api/board-staff/actions/{id}/restore} — 되돌림 사유 1~200자(정책 6) */
public record BoardStaffActionRestoreRequest(String reason) {
}

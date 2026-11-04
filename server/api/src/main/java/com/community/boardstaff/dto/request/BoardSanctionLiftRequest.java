package com.community.boardstaff.dto.request;

/** {@code POST /api/board-staff/sanctions/{id}/lift} — 해제 사유 1~200자(결정 11) */
public record BoardSanctionLiftRequest(String reason) {
}

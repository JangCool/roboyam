package com.community.sanction.dto.request;

/** 운영자의 스텝 처리 되돌리기 요청 — 사유 1~200자(board-staff-action-contract 정책 6) */
public record BoardStaffActionRestoreReasonRequest(String reason) {
}

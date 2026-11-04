package com.community.sanction.dto.response;

/** {@code GET /api/admin/appeals/summary} — 대기 수(메뉴 뱃지·대시보드) */
public record AppealSummaryView(long pending) {
}

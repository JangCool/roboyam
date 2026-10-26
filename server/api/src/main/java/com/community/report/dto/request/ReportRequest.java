package com.community.report.dto.request;

/**
 * {@code POST /api/reports}(user-web {@code ReportRequest}) — 회원만.
 *
 * @param targetType POST · COMMENT · GROUP(소모임 — V95)
 * @param targetId   글 ID · 댓글 ID · 소모임 ID
 * @param reason     글·댓글: SPAM_AD · ABUSE · OBSCENE · ILLEGAL_FILMING · RIGHTS · OTHER / 소모임: PROMOTION · OFFLINE_RISK · STOCK_RECOMMENDATION(투자 고지 게시판의 소모임만) · OTHER
 * @param detail     기타 사유의 설명(필수, 300자 이하) — 다른 사유면 선택
 */
public record ReportRequest(String targetType, String targetId, String reason, String detail) {
}

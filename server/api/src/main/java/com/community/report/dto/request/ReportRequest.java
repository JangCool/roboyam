package com.community.report.dto.request;

/**
 * {@code POST /api/reports}(user-web {@code ReportRequest}) — 회원만.
 *
 * @param targetType POST · COMMENT
 * @param reason     SPAM_AD · ABUSE · OBSCENE · ILLEGAL_FILMING · RIGHTS · OTHER
 * @param detail     기타 사유의 설명(필수, 300자 이하) — 다른 사유면 선택
 */
public record ReportRequest(String targetType, String targetId, String reason, String detail) {
}

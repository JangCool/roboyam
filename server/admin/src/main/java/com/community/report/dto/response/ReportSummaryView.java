package com.community.report.dto.response;

/** 열린 사건 수 — 처리 대기 · 검토 중 */
public record ReportSummaryView(long pending, long inReview) {
}

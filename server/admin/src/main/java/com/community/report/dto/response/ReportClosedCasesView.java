package com.community.report.dto.response;

/** 작성자 대상의 최근 90일 닫힌 사건 수 — 조치 · 무시 */
public record ReportClosedCasesView(long resolved, long dismissed) {
}

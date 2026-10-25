package com.community.report.dto.response;

/** 일괄 무시에서 처리하지 못한 사건 — {@code code} REPORT_CASE_NOT_FOUND · REPORT_CASE_CLOSED · INTERNAL */
public record ReportBulkFailureView(String id, String code) {
}

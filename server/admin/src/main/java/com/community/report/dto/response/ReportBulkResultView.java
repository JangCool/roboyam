package com.community.report.dto.response;

import java.util.List;

/** 일괄 무시 결과 — 무시한 사건 ID · 못 한 사건 */
public record ReportBulkResultView(List<String> done, List<ReportBulkFailureView> failed) {
}

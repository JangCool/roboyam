package com.community.report.dto.response;

import java.util.List;

/** 사건 목록 한 쪽 — {@code page} 는 0부터 */
public record ReportCaseListView(long total, int page, int size, List<ReportCaseItemView> items) {
}

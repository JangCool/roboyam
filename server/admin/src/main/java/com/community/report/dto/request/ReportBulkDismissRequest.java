package com.community.report.dto.request;

import java.util.List;

/** 일괄 무시 — 사건 ID 1~50개, 허위 표시, 글(500자까지) */
public record ReportBulkDismissRequest(List<String> ids, Boolean falseReport, String note) {
}

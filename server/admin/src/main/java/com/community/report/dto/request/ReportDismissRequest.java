package com.community.report.dto.request;

/** 신고 사건 무시 — 사유 코드·글은 고르면(글 500자까지), {@code falseReport} 허위 신고로 표시(신고 남용 차단이 센다) */
public record ReportDismissRequest(String reasonCode, String note, Boolean falseReport) {
}

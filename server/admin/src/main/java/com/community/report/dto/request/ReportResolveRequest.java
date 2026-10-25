package com.community.report.dto.request;

/** 신고 사건 조치 — {@code action} HIDE · DELETE · KEEP_HIDDEN, {@code reasonCode} 신고 사유 코드, {@code note} 1~500자(작성자 알림에 간다) */
public record ReportResolveRequest(String action, String reasonCode, String note) {
}

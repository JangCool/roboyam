package com.community.report.dto.request;

/** 신고 사건 조치 — {@code action} HIDE · DELETE · KEEP_HIDDEN · DEACTIVATE(소모임 사건만 — note 1~200자, 모임장에게 사유로 간다), {@code reasonCode} 사건 대상 체계의 신고 사유 코드, {@code note} 1~500자(작성자 알림에 간다) */
public record ReportResolveRequest(String action, String reasonCode, String note) {
}

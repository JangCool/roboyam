package com.community.report.dto.response;

import java.time.Instant;

/** 사건의 신고 한 건 — {@code reason} 사유 코드, {@code memo} 신고자가 적은 설명, {@code status} PENDING · ACCEPTED · REJECTED · DISMISSED */
public record ReportLineView(String id, ReportReporterView reporter, String reason, String memo, Instant createdAt, String status) {
}

package com.community.report.dto.response;

/** 신고자(운영자에게만) — 탈퇴했으면 {@code withdrawn} */
public record ReportReporterView(String id, String name, boolean withdrawn) {
}

package com.community.report.dto.response;

/**
 * 대상 원문 — {@code format} TEXT · HTML, {@code body} 는 지금 원문(파기했으면 null), {@code snapshot} 은 첫 신고 때의 스냅샷(보유기간 배치가 신고를 지웠으면 null)
 */
public record ReportContentView(String format, String body, ReportSnapshotView snapshot) {
}

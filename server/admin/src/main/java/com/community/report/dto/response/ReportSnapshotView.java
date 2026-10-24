package com.community.report.dto.response;

/** 첫 신고 때 남긴 대상 — 제목(댓글이면 null)·본문(보이는 글자, 앞 2,000자)·작성자 표시 이름 */
public record ReportSnapshotView(String title, String body, String authorName) {
}

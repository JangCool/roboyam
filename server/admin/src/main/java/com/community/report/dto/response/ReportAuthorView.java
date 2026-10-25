package com.community.report.dto.response;

/** 대상 작성자 — {@code kind} MEMBER · GUEST, 비회원은 id null·이름은 그때 이름 */
public record ReportAuthorView(String kind, String id, String name, boolean withdrawn) {
}

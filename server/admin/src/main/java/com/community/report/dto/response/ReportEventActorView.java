package com.community.report.dto.response;

/** 기록의 행위자 — {@code kind} OPERATOR · SYSTEM · MEMBER. 신고(MEMBER)는 신고자를 남기지 않아 id·name null */
public record ReportEventActorView(String kind, String id, String name) {
}

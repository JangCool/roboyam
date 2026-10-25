package com.community.report.dto.response;

/** 운영자 한 명(검토자·처리자) — 지워진 운영자면 이름 null */
public record ReportOperatorRefView(String id, String name) {
}

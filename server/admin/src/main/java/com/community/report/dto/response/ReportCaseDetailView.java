package com.community.report.dto.response;

import java.util.List;

/**
 * 사건 상세 — 계약의 {@code case} 칸(자바 예약어라 이름만 {@code caseItem} 으로 두고 JSON 은 {@code case}).
 *
 * @param publicUrl 공개 사이트의 글 주소(사이트 공개 주소가 없으면 null)
 */
public record ReportCaseDetailView(@com.fasterxml.jackson.annotation.JsonProperty("case") ReportCaseItemView caseItem, ReportContentView content,
                                   List<ReportLineView> reports, ReportAuthorCardView authorCard, List<ReportEventView> events, String publicUrl) {
}

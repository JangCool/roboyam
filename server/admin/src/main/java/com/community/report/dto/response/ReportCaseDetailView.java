package com.community.report.dto.response;

import java.util.List;

/**
 * 사건 상세 — 계약의 {@code case} 칸(자바 예약어라 이름만 {@code caseItem} 으로 두고 JSON 은 {@code case}).
 *
 * @param publicUrl 공개 사이트의 대상 주소 — 글·댓글 사건은 그 글({@code /posts/{id}}), 소모임 사건은 그 소모임({@code /groups/{id}}). 사이트 공개 주소가 없으면 null
 * @param group     소모임 사건(V95)의 대상 소모임 — 글·댓글 사건이거나 소모임 행이 없으면 null
 */
public record ReportCaseDetailView(@com.fasterxml.jackson.annotation.JsonProperty("case") ReportCaseItemView caseItem, ReportContentView content,
                                   List<ReportLineView> reports, ReportAuthorCardView authorCard, List<ReportEventView> events, String publicUrl,
                                   ReportGroupView group) {
}

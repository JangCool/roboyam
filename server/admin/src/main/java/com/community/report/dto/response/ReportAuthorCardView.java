package com.community.report.dto.response;

import java.time.Instant;

/**
 * 작성자 카드 — 비회원이면 id·grade·joinedAt null, 닫힌 사건 수 0. {@code grade} 는 등급 키. {@code sanction} 은 제재 연결 전이라 늘 null(커머스 병합 뒤)
 */
public record ReportAuthorCardView(String id, String name, String grade, Instant joinedAt, ReportClosedCasesView closedCases, Object sanction) {
}

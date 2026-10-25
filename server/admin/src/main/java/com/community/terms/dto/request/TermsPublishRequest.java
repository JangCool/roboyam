package com.community.terms.dto.request;

/**
 * 새 버전 게시 — 초안 본문으로 만든다. 검사는 서비스가 한다({@code fields.effectiveDate}·{@code fields.changeNote}·{@code fields.draft}).
 *
 * @param effectiveDate 시행일 {@code YYYY-MM-DD}(사이트 표시 시간대 기준)
 * @param changeNote    변경 요지(선택, 0~200자)
 */
public record TermsPublishRequest(String effectiveDate, String changeNote) {
}

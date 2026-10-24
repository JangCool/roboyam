package com.community.terms.dto.response;

/**
 * 변경 안내.
 *
 * @param upcoming   시행 예정 버전이면 true, 이미 시행된 최근 변경이면 false
 * @param changeNote 변경 요지(없으면 null)
 */
public record TermsNoticeView(int version, String effectiveDate, boolean upcoming, String changeNote) {
}

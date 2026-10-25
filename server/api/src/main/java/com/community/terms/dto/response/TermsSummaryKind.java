package com.community.terms.dto.response;

/**
 * @param kind    {@code TERMS}·{@code PRIVACY}
 * @param current 지금 약관(없으면 null)
 * @param notice  변경 안내(없으면 null) — 시행 예정이 있거나, 지금 약관이 2번 이상이고 시행된 지 7일 안일 때
 */
public record TermsSummaryKind(String kind, TermsCurrentBrief current, TermsNoticeView notice) {
}

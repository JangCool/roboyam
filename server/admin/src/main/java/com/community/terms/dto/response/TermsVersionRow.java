package com.community.terms.dto.response;

import java.time.Instant;

/**
 * 이력 표의 한 줄.
 *
 * @param publishedBy 게시한 운영자 표시 이름(없으면 null)
 * @param status      {@code CURRENT}·{@code UPCOMING}·{@code PAST}
 */
public record TermsVersionRow(int version, String effectiveDate, Instant publishedAt, String publishedBy, String changeNote, String status) {
}

package com.community.terms.dto.response;

/** 게시본 목록의 한 줄 — {@code status} 는 {@code CURRENT}·{@code UPCOMING}·{@code PAST} */
public record PublicTermsHistoryItem(int version, String effectiveDate, String status) {
}

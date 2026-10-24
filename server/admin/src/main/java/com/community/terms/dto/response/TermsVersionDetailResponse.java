package com.community.terms.dto.response;

import java.time.Instant;

/** 게시본 한 버전(본문 포함) — {@code GET …/versions/{version}}, 게시 결과 */
public record TermsVersionDetailResponse(int version, String effectiveDate, Instant publishedAt, String publishedBy, String changeNote, String status,
        String body) {

    @Override
    public String toString() {
        return "TermsVersionDetailResponse[version=" + version + ", effectiveDate=" + effectiveDate + ", status=" + status + "]";
    }
}

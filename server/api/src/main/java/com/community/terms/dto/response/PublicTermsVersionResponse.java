package com.community.terms.dto.response;

import java.time.Instant;

/** {@code GET /api/terms/{kind}/versions/{version}} — 게시본 한 버전(본문 포함) */
public record PublicTermsVersionResponse(int version, String effectiveDate, Instant publishedAt, String changeNote, String status, String body) {

    @Override
    public String toString() {
        return "PublicTermsVersionResponse[version=" + version + ", effectiveDate=" + effectiveDate + ", status=" + status + "]";
    }
}

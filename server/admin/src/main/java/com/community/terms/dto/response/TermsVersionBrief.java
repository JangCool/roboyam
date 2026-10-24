package com.community.terms.dto.response;

import java.time.Instant;

/** 개요의 지금·시행 예정 버전 — {@code effectiveDate} 는 {@code YYYY-MM-DD} */
public record TermsVersionBrief(int version, String effectiveDate, Instant publishedAt) {
}

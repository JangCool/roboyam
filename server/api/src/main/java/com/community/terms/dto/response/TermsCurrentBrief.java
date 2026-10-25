package com.community.terms.dto.response;

/** 지금 약관의 버전·시행일({@code YYYY-MM-DD}) */
public record TermsCurrentBrief(int version, String effectiveDate) {
}

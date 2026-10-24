package com.community.terms.dto.response;

/** 시행 예정 버전(본문은 {@code …/versions/{version}} 으로 본다) */
public record PublicTermsUpcoming(int version, String effectiveDate, String changeNote) {
}

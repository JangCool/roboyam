package com.community.identity.dto.response;

import java.time.Instant;

/**
 * 내 본인인증 상태 — {@code GET /api/identity-verifications/me} 와 인증 완료의 응답. 생년월일·CI·DI 는 싣지 않는다 — 만 19세 이상인가와 19세 생일만.
 *
 * @param verifiedAt 인증한 순간(미인증이면 null)
 * @param adult      지금(서비스 날짜) 만 19세 이상인가(미인증이면 null)
 * @param adultFrom  만 19세가 되는 서비스 날짜 {@code YYYY-MM-DD}(미인증이면 null)
 */
public record IdentityStatusView(boolean verified, Instant verifiedAt, Boolean adult, String adultFrom) {

    public static IdentityStatusView unverified() {
        return new IdentityStatusView(false, null, null, null);
    }
}

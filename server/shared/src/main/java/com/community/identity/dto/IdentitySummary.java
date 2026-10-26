package com.community.identity.dto;

import java.time.Instant;

/**
 * 관리자 회원 상세의 「본인 인증」 구획(계약 {@code identity: { verified, verifiedAt, adult, provider }}) — 생년월일·해시는 싣지 않는다.
 *
 * @param adult 지금(서비스 날짜) 만 19세 이상인가
 */
public record IdentitySummary(boolean verified, Instant verifiedAt, boolean adult, IdentityProviderKind provider) {
}

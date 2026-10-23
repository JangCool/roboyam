package com.community.mfa.dto.response;

import java.time.Instant;

/**
 * 신뢰 기기 한 대.
 *
 * @param agent   접속 환경 요약({@code Chrome / Windows / Desktop} — 로그인 이력과 같은 요약)
 * @param current 지금 이 브라우저인가
 */
public record MfaTrustedDeviceView(String id, String agent, String ip, Instant trustedAt, Instant expiresAt, Instant lastUsedAt, boolean current) {
}

package com.community.mfa.dto.response;

import java.time.Instant;

/**
 * 로그인 보안 설정(루트만).
 *
 * @param enabledAt    마지막으로 켠 시각(켠 적 없으면 null)
 * @param enabledBy    마지막으로 켠 운영자 이름(없거나 지워졌으면 null)
 * @param rootEnrolled 루트 본인이 OTP 를 등록했는가 — 아니면 켤 수 없다
 */
public record LoginSecurityView(boolean enabled, Instant enabledAt, String enabledBy, int graceDays, int trustMonths, int maxFailures, boolean rootEnrolled,
                                LoginSecuritySummary summary) {
}

package com.community.mfa.dto.response;

import java.time.Instant;

/**
 * 내 2차 인증 상태.
 *
 * @param recoveryCodesLeft 쓰지 않은 복구 코드(0~10)
 * @param graceEndsAt       켜져 있고 미등록일 때 유예 마감. 그 밖은 null
 * @param trustDays         기기 신뢰 일수(설정 개월 × 30)
 */
public record MfaMeView(boolean siteEnabled, boolean enrolled, Instant enrolledAt, Instant lastUsedAt, int recoveryCodesLeft, Instant graceEndsAt,
                        int trustDays) {
}

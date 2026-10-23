package com.community.mfa.dto.response;

import java.time.Instant;

/**
 * 운영자 상세의 2차 인증 상태.
 *
 * @param graceEndsAt    켜져 있고 미등록일 때 유예 마감. 그 밖은 null
 * @param graceExpired   켜져 있고 미등록이며 유예가 지났는가
 * @param trustedDevices 기간이 남은 신뢰 기기 수
 */
public record OperatorMfaView(boolean siteEnabled, boolean enrolled, Instant enrolledAt, Instant graceEndsAt, boolean graceExpired, int trustedDevices) {
}

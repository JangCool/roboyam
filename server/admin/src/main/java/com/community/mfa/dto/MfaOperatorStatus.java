package com.community.mfa.dto;

import java.time.Instant;

/** 등록 현황을 셀 때의 활성 운영자 한 명 */
public record MfaOperatorStatus(long operatorId, String operatorName, Instant createdAt, boolean enrolled) {
}

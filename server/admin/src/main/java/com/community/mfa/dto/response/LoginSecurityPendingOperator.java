package com.community.mfa.dto.response;

import java.time.Instant;

/** 등록 현황의 미등록 운영자 한 명 — 유예 마감(꺼져 있으면 null) */
public record LoginSecurityPendingOperator(String operatorId, String name, Instant graceEndsAt) {
}

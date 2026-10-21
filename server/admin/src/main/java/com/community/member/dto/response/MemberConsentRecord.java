package com.community.member.dto.response;

import java.time.Instant;

/** 필수 동의 한 건 — 동의한 시각과 동의한 문서의 버전 */
public record MemberConsentRecord(Instant agreedAt, String version) {
}

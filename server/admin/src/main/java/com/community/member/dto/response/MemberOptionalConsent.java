package com.community.member.dto.response;

import java.time.Instant;

/** 선택 동의 한 항목 — {@code key} 는 {@code MARKETING} · {@code EMAIL_AD} · {@code THIRD_PARTY} · {@code PROFILE_PUBLIC}. 선택 동의 표가 아직 없어 늘 빈 목록으로 내려간다 */
public record MemberOptionalConsent(String key, boolean agreed, Instant agreedAt) {
}

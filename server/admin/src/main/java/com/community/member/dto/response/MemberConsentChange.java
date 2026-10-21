package com.community.member.dto.response;

import java.time.Instant;

/** 동의 변경·철회 이력 한 줄 — 이력 표가 아직 없어 늘 빈 목록으로 내려간다. {@code channel} 은 {@code MYPAGE} · {@code ADMIN} */
public record MemberConsentChange(String changeId, String item, String action, String channel, Instant changedAt) {
}

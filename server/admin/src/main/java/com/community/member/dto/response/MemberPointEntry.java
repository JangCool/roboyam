package com.community.member.dto.response;

import java.time.Instant;

/**
 * 포인트 내역 한 줄 — admin-web {@code MemberPointEntry}. 포인트 기능이 아직 없어 상세의 목록은 늘 빈 배열이다.
 *
 * @param kind  {@code EARN} · {@code USE} · {@code REVOKE}
 * @param delta 실제 반영된 증감(−는 차감)
 */
public record MemberPointEntry(String entryId, String kind, long delta, String reason, Instant createdAt) {
}

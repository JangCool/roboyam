package com.community.member.dto.response;

import java.time.Instant;

/**
 * 포인트 내역 한 줄 — admin-web {@code MemberPointEntry}. 회원 상세에는 원장의 최근 {@code MemberGradePointAssembler#POINT_ENTRY_LIMIT} 줄이 최신순으로 실린다.
 *
 * @param kind  {@code EARN} · {@code USE} · {@code REVOKE}
 * @param delta 실제 반영된 증감(−는 차감)
 */
public record MemberPointEntry(String entryId, String kind, long delta, String reason, Instant createdAt) {
}

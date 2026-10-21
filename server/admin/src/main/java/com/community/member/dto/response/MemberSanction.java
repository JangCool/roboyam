package com.community.member.dto.response;

import java.time.Instant;

/**
 * 제재 이력 한 건 — 제재 표가 아직 없어 상세의 목록은 늘 빈 배열이다.
 *
 * @param kind   {@code WARNING} · {@code SUSPENSION} · {@code BAN}
 * @param scope  {@code SITE} · {@code BOARD}
 * @param status {@code ACTIVE} · {@code ENDED} · {@code RELEASED}
 */
public record MemberSanction(String sanctionId, String kind, String scope, String boardName, Instant occurredAt, Instant endsAt, String status, String reason) {
}

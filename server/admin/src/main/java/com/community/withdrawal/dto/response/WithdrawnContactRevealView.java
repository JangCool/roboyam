package com.community.withdrawal.dto.response;

import java.time.Instant;

/**
 * 탈퇴 회원 연락처 열람 기록 한 줄(감사 {@code WITHDRAWN_CONTACT_REVEAL} 에서 읽는다) — 최근 것부터.
 *
 * @param operatorName 열람한 운영자 이름(운영자가 지워졌으면 null)
 */
public record WithdrawnContactRevealView(String operatorName, Instant revealedAt, String reason) {
}

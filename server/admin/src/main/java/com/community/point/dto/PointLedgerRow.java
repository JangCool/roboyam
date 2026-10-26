package com.community.point.dto;

import java.time.Instant;

/**
 * 원장 한 줄의 원본(조회가 읽은 값). 회원 상세의 최근 내역은 닉네임·처리자를 읽지 않아 null 이다.
 *
 * @param memberNickname 지금 닉네임
 * @param freeDelta      그중 무상 몫(부호 포함)
 * @param paidDelta      그중 유상 몫(부호 포함) — {@code freeDelta + paidDelta = delta}
 * @param actorName      운영 조정을 한 운영자의 이름 — 자동 지급·회수이거나 운영자가 지워졌으면 null
 */
public record PointLedgerRow(Long id, Long userId, String memberNickname, String kind, String reason, String note, Integer delta, Long freeDelta, Long paidDelta,
                             Long balanceAfter, String actorName, Instant createdAt) {
}

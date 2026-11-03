package com.community.withdrawal.dto;

import com.community.withdrawal.entity.WithdrawalPendingCode;
import java.time.Instant;

/**
 * 남은 일 한 건 — 화면 타입 {@code WithdrawalPendingItem}.
 *
 * @param label     식별 글(주문번호·신청 번호 등). 화면은 가공하지 않는다
 * @param link      화면 경로(없으면 null)
 * @param waitUntil 언제쯤 끝나는지 알면 그 시각(반품 기한·광고 끝), 모르면 null
 */
public record WithdrawalPending(WithdrawalPendingCode code, String label, String link, Instant waitUntil) {
}

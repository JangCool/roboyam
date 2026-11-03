package com.community.withdrawal.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code POST /api/me/withdrawal} 의 답 — 화면 타입 {@code WithdrawalResult}. {@code COMPLETED} 면 세션은 끝나 있다.
 *
 * @param state {@code COMPLETED} · {@code SCHEDULED}
 */
public record WithdrawalResult(String state, List<WithdrawalView.PendingItem> pending, Instant expectedCompleteAt, Instant rejoinAvailableAt, boolean hasPaymentHistory,
                               int retentionYears, String supportEmail) {

    public boolean completed() {
        return "COMPLETED".equals(state);
    }
}

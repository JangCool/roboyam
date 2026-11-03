package com.community.withdrawal.dto.request;

import java.util.List;

/**
 * {@code POST /api/me/withdrawal} — 화면 타입 {@code WithdrawalRequest}(member-withdrawal-contract). 검사 순서가 계약이라 {@code @Valid} 없이 서비스가 본다.
 *
 * @param password        비밀번호가 있는 계정만(소셜 전용은 null)
 * @param reason          6종 또는 null
 * @param reasonNote      0~200자
 * @param paidPointChoice {@code REFUND}·{@code FORFEIT}(필요 없으면 무시)
 * @param deliveredChoice {@code WAIT}·{@code NOW}
 * @param bizChoice       {@code WAIT}·{@code FORFEIT}
 * @param groupChoices    내가 모임장인 소모임의 처리(빠진 모임은 AUTO)
 * @param forfeitConsent  포기 동의 금액(서버 계산과 같아야 한다)
 */
public record WithdrawalRequest(String password, String reason, String reasonNote, String paidPointChoice, String deliveredChoice, String bizChoice,
                                List<GroupChoice> groupChoices, ForfeitConsent forfeitConsent) {

    public record GroupChoice(String groupId, String action, String toMemberId) {
    }

    public record ForfeitConsent(Long paidPoints, Long refundEstimate, Long bizForfeit) {
    }
}

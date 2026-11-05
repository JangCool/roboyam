package com.community.withdrawal.dto.request;

import java.util.List;

/**
 * {@code POST /api/me/withdrawal} — 화면 타입 {@code WithdrawalRequest}(member-withdrawal-contract). 검사 순서가 계약이라 {@code @Valid} 없이 서비스가 본다.
 *
 * @param password              비밀번호가 있는 계정만(소셜 전용은 null)
 * @param reason                6종 또는 null
 * @param reasonNote            0~200자
 * @param paidPointChoice       {@code REFUND}·{@code FORFEIT}(필요 없으면 무시)
 * @param deliveredChoice       {@code WAIT}·{@code NOW}
 * @param bizChoice             {@code WAIT}·{@code FORFEIT}
 * @param groupChoices          내가 모임장인 소모임의 처리(빠진 모임은 AUTO)
 * @param forfeitConsent        포기 동의 금액(서버 계산과 같아야 한다)
 * @param waiveEmoticonEarnings 정산 정보 없이 남은 로보티콘 수익을 받지 않기로 동의(V148 — 그때만 본다: 남은 수익이 있고 정산 정보가 없을 때). true 면 {@code forfeitConsent.emoticonEarnings}
 *                              가 서버 금액과 같아야 한다. 없거나 false 면 409 {@code WITHDRAWAL_EMOTICON_EARNINGS_UNPAID}
 */
public record WithdrawalRequest(String password, String reason, String reasonNote, String paidPointChoice, String deliveredChoice, String bizChoice,
                                List<GroupChoice> groupChoices, ForfeitConsent forfeitConsent, Boolean waiveEmoticonEarnings) {

    /** 로보티콘 수익 포기 칸이 없던 모양(기존 호출) */
    public WithdrawalRequest(String password, String reason, String reasonNote, String paidPointChoice, String deliveredChoice, String bizChoice,
                             List<GroupChoice> groupChoices, ForfeitConsent forfeitConsent) {
        this(password, reason, reasonNote, paidPointChoice, deliveredChoice, bizChoice, groupChoices, forfeitConsent, null);
    }

    public record GroupChoice(String groupId, String action, String toMemberId) {
    }

    /** {@code emoticonEarnings} = 탈퇴 화면이 보인 로보티콘 남은 수익({@code emoticonCreator.unpaidEarnings} — V148) */
    public record ForfeitConsent(Long paidPoints, Long refundEstimate, Long bizForfeit, Long emoticonEarnings) {

        public ForfeitConsent(Long paidPoints, Long refundEstimate, Long bizForfeit) {
            this(paidPoints, refundEstimate, bizForfeit, null);
        }
    }

    public boolean waivesEmoticonEarnings() {
        return Boolean.TRUE.equals(waiveEmoticonEarnings);
    }
}

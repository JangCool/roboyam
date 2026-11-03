package com.community.withdrawal.dto;

/**
 * 회원이 탈퇴 화면에서 고른 것(member-withdrawal-contract 「필드·검증」) — 필요 없는 상황의 값은 무시하고, 필요한데 없으면 기본값(배송·광고 = WAIT, 충전 = REFUND).
 *
 * @param paidPointChoice {@code REFUND} · {@code FORFEIT}(충전 포인트 — 지금은 늘 0 이라 뜻이 없다)
 * @param deliveredChoice {@code WAIT}(반품 기한까지 기다렸다 자동 탈퇴) · {@code NOW}(지금 탈퇴)
 * @param bizChoice       {@code WAIT}(광고·핫딜이 끝날 때까지) · {@code FORFEIT}(남은 기간 포기)
 */
public record WithdrawalChoices(String paidPointChoice, String deliveredChoice, String bizChoice) {

    public static final String REFUND = "REFUND";
    public static final String FORFEIT = "FORFEIT";
    public static final String WAIT = "WAIT";
    public static final String NOW = "NOW";

    /** 기본값 — 환불 · 기다리기 · 기다리기 */
    public static final WithdrawalChoices DEFAULT = new WithdrawalChoices(REFUND, WAIT, WAIT);

    public WithdrawalChoices {
        paidPointChoice = FORFEIT.equals(paidPointChoice) ? FORFEIT : REFUND;
        deliveredChoice = NOW.equals(deliveredChoice) ? NOW : WAIT;
        bizChoice = FORFEIT.equals(bizChoice) ? FORFEIT : WAIT;
    }

    public boolean forfeitPaidPoints() {
        return FORFEIT.equals(paidPointChoice);
    }

    public boolean waitDelivered() {
        return WAIT.equals(deliveredChoice);
    }

    public boolean forfeitBiz() {
        return FORFEIT.equals(bizChoice);
    }
}

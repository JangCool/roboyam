package com.community.withdrawal.entity;

/**
 * 탈퇴 신청의 경로({@code member_withdrawals.source}, shared V134) — 회원 본인의 신청 · 운영자 강제 탈퇴(member-withdrawal-contract 「관리자」).
 * 운영자 대리 취소는 {@link #MEMBER} 신청에만 할 수 있다.
 */
public enum WithdrawalSource {
    MEMBER,
    OPERATOR
}

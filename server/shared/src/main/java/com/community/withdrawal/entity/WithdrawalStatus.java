package com.community.withdrawal.entity;

/** 탈퇴 신청 기록({@code member_withdrawals.status})의 상태 — 진행 중(예약) · 완료(익명화) · 취소(회원이 되돌림) */
public enum WithdrawalStatus {
    SCHEDULED,
    COMPLETED,
    CANCELED
}

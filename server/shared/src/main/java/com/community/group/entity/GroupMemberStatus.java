package com.community.group.entity;

/** 멤버 행의 상태 — 멤버 · 가입 신청 대기 · 신청 거절 · 탈퇴 · 강퇴 */
public enum GroupMemberStatus {
    ACTIVE,
    APPLIED,
    REJECTED,
    LEFT,
    KICKED
}

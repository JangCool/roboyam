package com.community.membernotification.entity;

/** 회원 이벤트를 회원이 끌 수 없게 코드가 잠근 까닭(admin-web {@code MemberEventLockReason}) */
public enum MemberEventLockReason {
    /** 계정 알림 — 포인트 회수 · 신고로 숨김 */
    ACCOUNT,
    /** 처리 알림 — 운영자 · 스텝이 내 글·댓글을 처리함(사유와 함께 보낸다) */
    ACTION
}

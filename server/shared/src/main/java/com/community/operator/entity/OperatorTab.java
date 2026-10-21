package com.community.operator.entity;

/** 운영자 목록을 가르는 탭 — 서버가 나눈다. ACTIVE 쓰는 사람 · PENDING 승인 대기 · BLOCKED 못 쓰는 사람(잠금·만료·사용 중지·기간 밖) */
public enum OperatorTab {
    ACTIVE,
    PENDING,
    BLOCKED
}

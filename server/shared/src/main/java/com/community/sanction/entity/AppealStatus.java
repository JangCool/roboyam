package com.community.sanction.entity;

/** 이의제기 상태 — 대기 · 인용 · 기각. 값은 표 {@code sanction_appeals.status}(V136 CHECK)와 같다 */
public enum AppealStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

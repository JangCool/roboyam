package com.community.sanction.entity;

/**
 * 제재 상태(정책 5) — 유효·진행 중 · 기간 끝 또는 영구 정지로 대체 · 운영자 해제(정지·영구만, 횟수에 남음) · 이의제기 인용으로 무효(횟수에서 빠짐).
 * 값은 표 {@code member_sanctions.status}(V136 CHECK)와 같다.
 */
public enum SanctionStatus {
    ACTIVE,
    ENDED,
    LIFTED,
    VOIDED
}

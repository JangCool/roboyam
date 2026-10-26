package com.community.restriction.entity;

/** 기능 제한의 지금 상태 — 저장하지 않고 읽을 때 지금 시각으로 판정한다 */
public enum RestrictionStatus {
    /** 진행 중 — 해제 안 됨 ∧ 시작 ≤ 지금 ∧ (끝 없음 ∨ 끝 > 지금) */
    ACTIVE,
    /** 기간이 끝남 */
    EXPIRED,
    /** 운영자가 해제함 */
    LIFTED
}

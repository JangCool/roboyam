package com.community.rewardevent.entity;

/** 받는 사람 상태 — 대기 · 완료(건너뜀·전달 대기/완료 포함) · 일부 실패 · 실패 · 모두 건너뜀 · 회수됨 */
public enum RewardRecipientStatus {
    PENDING, GRANTED, PARTIAL, FAILED, SKIPPED, REVOKED
}

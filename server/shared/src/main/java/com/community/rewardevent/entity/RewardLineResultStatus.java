package com.community.rewardevent.entity;

/** 보상 줄 하나의 결과 — 대기 · 성공 · 실패 · 건너뜀 · 회수됨 · (직접 전달) 전달 대기 · 전달 완료 */
public enum RewardLineResultStatus {
    PENDING, SUCCESS, FAILED, SKIPPED, REVOKED, DELIVERY_PENDING, DELIVERED
}

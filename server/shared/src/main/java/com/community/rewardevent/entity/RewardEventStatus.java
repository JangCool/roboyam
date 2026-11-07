package com.community.rewardevent.entity;

/** 보상 이벤트 상태 — 준비 → 진행 중 → 마감 → 지급 끝, 지급 전이면 취소(V163 ck_reward_events_status, event-admin-contract 「상태·방식」) */
public enum RewardEventStatus {
    DRAFT, ACTIVE, CLOSED, DONE, CANCELED
}

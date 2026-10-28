package com.community.groupbuy.entity;

/** 환불 기록 상태(결정 16·36) — 진행 중 → 완료 / 지연(PG 포기) / 수동 */
public enum RefundStatus {
    IN_PROGRESS, COMPLETED, DELAYED, MANUAL
}

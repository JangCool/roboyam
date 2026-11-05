package com.community.emoticon.entity;

/**
 * 작가 월 정산 상태(V145 {@code ck_emoticon_settlements_status} — 두 앱 목업 {@code EmoticonSettlementStatus}).
 * {@code ESTIMATED}(예정 — 금액을 조회 때 계산) → {@code CONFIRMED}(확정 — 금액을 굳힘) → {@code PAID}(지급 완료 기록) · 예정·확정 → {@code HELD}(보류 — 사유) → 보류 전 상태.
 */
public enum EmoticonSettlementStatus {
    ESTIMATED,
    CONFIRMED,
    PAID,
    HELD
}

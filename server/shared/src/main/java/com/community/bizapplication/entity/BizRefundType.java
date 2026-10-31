package com.community.bizapplication.entity;

/** 환불 종류(H1·H2·F5) — 시작 전 전액 자동 · 진행 중 일할 심사 · 정지로 내린 진행 중 건의 일할 심사 · 긴급 내림 되돌리기의 중단 기간 일할 환불(바로 승인) */
public enum BizRefundType {
    FULL_AUTO, PRORATED, SUSPEND_PRORATED, DOWNTIME
}

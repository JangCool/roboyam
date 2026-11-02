package com.community.bizapplication.entity;

/**
 * 환불 종류(H1·H2·F5) — 시작 전 전액 자동 · 진행 중 일할 심사(옛 길 — V127 뒤로 새로 생기지 않는다) · 정지로 내린 진행 중 건의 일할 심사 · 긴급 내림 되돌리기의 중단 기간 일할 환불(바로 승인) ·
 * 광고주 요청 중단의 일할 − 중도 해지 수수료 심사(V127 — biz-pause-contract 규칙 5) · 운영자 추가 환불(클레임 — V128, 닫힌·끝난 건의 남은 금액을 바로 승인으로 돌려준다).
 */
public enum BizRefundType {
    FULL_AUTO, PRORATED, SUSPEND_PRORATED, DOWNTIME, STOP_PRORATED, CLAIM
}

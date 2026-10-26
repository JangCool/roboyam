package com.community.pointshop.entity;

/** 보유 상태 — 가짐 · 씀(소모형) · 회수(환불). 만료는 저장하지 않고 {@code expires_at} 으로 계산한다(결정 6) */
public enum UserItemStatus {
    OWNED,
    USED,
    REVOKED
}

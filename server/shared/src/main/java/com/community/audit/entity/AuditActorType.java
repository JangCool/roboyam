package com.community.audit.entity;

/** 감사 기록의 행위자 — 로그인한 운영자 · 시스템(배치 등) · 로그인하지 않은 사람(로그인 실패 등) */
public enum AuditActorType {
    OPERATOR,
    SYSTEM,
    ANONYMOUS
}

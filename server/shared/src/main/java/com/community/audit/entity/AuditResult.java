package com.community.audit.entity;

/** 감사 기록의 결과 — 성공 · 실패(로그인 실패 등) · 거부(권한 거부) */
public enum AuditResult {
    SUCCESS,
    FAILURE,
    DENIED
}

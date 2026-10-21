package com.community.permission.entity;

/** 운영자 권한 단위 — 조회(보기) · 처리(승인·삭제·상태 변경) · 설정(규칙·구성 변경) · 민감 조치(되돌리기 어려운 일) */
public enum PermissionUnit {
    READ,
    MANAGE,
    CONFIG,
    SENSITIVE
}

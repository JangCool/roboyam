package com.community.jjal.entity;

/** 짤 파일 상태 — 보임(ACTIVE) · 신고로 내려감(BLOCKED — 까닭·기한은 {@link JjalFileEntity}) */
public enum JjalFileStatus {
    ACTIVE,
    BLOCKED
}

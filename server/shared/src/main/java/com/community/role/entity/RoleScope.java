package com.community.role.entity;

/** 역할의 범위 — SITE 사이트 전체 · BOARD 게시판 하나(게시판 매니저·스텝). 운영자 역할은 늘 SITE */
public enum RoleScope {
    SITE,
    BOARD
}

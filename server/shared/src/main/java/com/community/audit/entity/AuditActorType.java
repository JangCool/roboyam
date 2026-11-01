package com.community.audit.entity;

/**
 * 감사 기록의 행위자 — 로그인한 운영자 · 시스템(배치 등) · 로그인하지 않은 사람(로그인 실패 등) · 회원(공개 사이트에서 운영 일을 하는 게시판 매니저·스텝 — 핫딜 종료·재개).
 * 회원 행위자의 {@code actor_id} 는 회원 ID 이고, 관리자 감사 조회는 그 회원의 지금 닉네임을 이름으로 낸다.
 */
public enum AuditActorType {
    OPERATOR,
    SYSTEM,
    ANONYMOUS,
    MEMBER
}

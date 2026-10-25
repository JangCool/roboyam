package com.community.restriction.entity;

/** 기능 제한을 건 쪽 — 표 {@code member_restrictions.source} 의 CHECK 와 같은 값 */
public enum RestrictionSource {
    /** 자동 규칙(예: 24시간 안 선점 3번 만료 → 24시간 공구 참여 제한) — {@code auto_rule} 이 있다, 감사의 행위자는 SYSTEM */
    AUTO,
    /** 운영자({@code sanction.manage}) */
    OPERATOR
}

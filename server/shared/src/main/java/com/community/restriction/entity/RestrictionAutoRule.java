package com.community.restriction.entity;

/**
 * 자동 부여 규칙 코드(표 {@code member_restrictions.auto_rule}) — 화면 이름은 번들 {@code restriction.autoRule.<코드>}, 회원에게 보이는 사유는 {@code restriction.autoRule.<코드>.reason}.
 */
public final class RestrictionAutoRule {

    /** 24시간 안에 공구 선점 3번 만료 → 24시간 공구 참여 제한(commerce.md 10-5 · 12-2) */
    public static final String GROUP_BUY_HOLD_EXPIRED = "GROUP_BUY_HOLD_EXPIRED";

    private RestrictionAutoRule() {
    }
}

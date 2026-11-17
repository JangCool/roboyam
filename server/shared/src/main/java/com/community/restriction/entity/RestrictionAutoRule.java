package com.community.restriction.entity;

/**
 * 자동 부여 규칙 코드(표 {@code member_restrictions.auto_rule}) — 화면 이름은 번들 {@code restriction.autoRule.<코드>}, 회원에게 보이는 사유는 {@code restriction.autoRule.<코드>.reason}.
 */
public final class RestrictionAutoRule {

    /** 24시간 안에 공구 선점 3번 만료 → 24시간 공구 참여 제한(commerce.md 10-5 · 12-2) */
    public static final String GROUP_BUY_HOLD_EXPIRED = "GROUP_BUY_HOLD_EXPIRED";

    /** 체험단 미반납 확정 → 영구 체험단 신청 금지(trial-group-contract 「미반납」) */
    public static final String TRIAL_UNRETURNED = "TRIAL_UNRETURNED";
    /** 체험단 사용기 기한 넘김 → 90일(설정) 체험단 신청 금지 */
    public static final String TRIAL_REVIEW_LATE = "TRIAL_REVIEW_LATE";
    /** 대여형 파손 회원 과실 판정 → 90일 체험단 신청 금지 */
    public static final String TRIAL_DAMAGE_FAULT = "TRIAL_DAMAGE_FAULT";
    /** 대여형 파손 고의·숨김 반납 판정 → 영구 체험단 신청 금지 */
    public static final String TRIAL_DAMAGE_INTENTIONAL = "TRIAL_DAMAGE_INTENTIONAL";

    private RestrictionAutoRule() {
    }
}

package com.community.restriction.entity;

/**
 * 기능 제한이 막을 수 있는 기능 — 그 기능을 하는 서비스가 {@code MemberRestrictionService#assertAllowed(userId, 기능)} 로 묻는다. 어느 종류가 막는지는 {@link RestrictionType#blocks}.
 */
public enum RestrictedFeature {
    /** 포인트 적립(글·댓글·추천 보너스 — 지금 있는 적립은 막히면 조용히 건너뛴다) */
    POINT_EARN,
    /** 포인트 사용(상점 구매·공구 포인트 결제) */
    POINT_USE,
    /** 새 충전 */
    CHARGE,
    /** 충전 포인트 환불 신청 — 어느 제한도 막지 않는다(commerce.md 12-3) */
    CHARGE_REFUND_REQUEST,
    /** 새 공구 참여 */
    GROUP_BUY_JOIN
}

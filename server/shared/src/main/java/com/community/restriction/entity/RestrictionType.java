package com.community.restriction.entity;

import java.util.Set;

/**
 * 기능 제한 종류(commerce.md 12-3) — 표 {@code member_restrictions.type} 의 CHECK 와 같은 값. 계정 정지({@code users.status})와 따로 걸린다.
 * 무엇을 막는지는 코드가 원천이다({@link #blocks}).
 */
public enum RestrictionType {

    /** 포인트 동결 — 포인트 적립·사용·충전을 막는다. 충전 포인트 환불 신청은 막지 않는다(회원 돈을 묶어 두지 않는다) */
    POINT_FREEZE(Set.of(RestrictedFeature.POINT_EARN, RestrictedFeature.POINT_USE, RestrictedFeature.CHARGE)),
    /** 공구 참여 제한 — 새 공구 참여만 막는다(이미 결제한 주문의 배송·취소·반품·조회는 된다) */
    GROUP_BUY_BAN(Set.of(RestrictedFeature.GROUP_BUY_JOIN)),
    /** 충전 제한 — 새 충전만 막는다(기존 포인트 사용·환불은 된다) */
    CHARGE_BAN(Set.of(RestrictedFeature.CHARGE));

    private final Set<RestrictedFeature> blocked;

    RestrictionType(Set<RestrictedFeature> blocked) {
        this.blocked = blocked;
    }

    /** 이 종류가 그 기능을 막는가 */
    public boolean blocks(RestrictedFeature feature) {
        return blocked.contains(feature);
    }
}

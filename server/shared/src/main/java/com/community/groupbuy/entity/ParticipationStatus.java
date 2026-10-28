package com.community.groupbuy.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 주문(참여) 상태({@code group_buy_participations.status}, commerce.md 10-3 · 계약 결정 46). 이름은 DB 값이다.
 */
public enum ParticipationStatus {
    PENDING_PAYMENT, JOINED, CONFIRMED, PREPARING, SHIPPED, DELIVERED, CANCELED, REFUNDED, EXPIRED;

    /** 진행 중 주문 — 공구당 1건(부분 유일 색인, 결정 23·28) */
    public static final Set<ParticipationStatus> IN_PROGRESS = EnumSet.of(PENDING_PAYMENT, JOINED, CONFIRMED, PREPARING, SHIPPED);

    /** 발송 전 결제된 주문 — 불발·중단이 환불하는 주문(결정 29) */
    public static final Set<ParticipationStatus> PAID_BEFORE_SHIPPING = EnumSet.of(JOINED, CONFIRMED, PREPARING);

    /** SQL IN 목록 */
    public static String sqlList(Set<ParticipationStatus> statuses) {
        List<String> names = statuses.stream().map(s -> "'" + s.name() + "'").toList();
        return String.join(", ", names);
    }
}

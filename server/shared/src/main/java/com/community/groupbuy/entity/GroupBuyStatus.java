package com.community.groupbuy.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * 공구 상태({@code group_buys.status}, commerce.md 10-2 · 계약 결정 49). 이름은 DB 값이라 한 번 정하면 바꾸지 않는다.
 * <pre>
 * DRAFT → RECRUITING → CLOSING → CLOSED_SUCCESS → ORDERED → SHIPPING → COMPLETED
 *                               ↘ COMPLETED(즉시 발송 — 진행 중 주문이 모두 끝나면)
 *                     ↘ CLOSED_FAILED(불발 → 전액 환불)
 * RECRUITING~SHIPPING → CANCELED(중단 — 발송 전 주문만 환불)
 * </pre>
 */
public enum GroupBuyStatus {
    DRAFT, RECRUITING, CLOSING, CLOSED_SUCCESS, CLOSED_FAILED, ORDERED, SHIPPING, COMPLETED, CANCELED;

    /** 운영자가 중단할 수 있는 상태(결정 29·49) */
    public static final Set<GroupBuyStatus> CANCELABLE = EnumSet.of(RECRUITING, CLOSING, CLOSED_SUCCESS, ORDERED, SHIPPING);

    /** 추가 차수의 원본이 될 수 있는 상태 — 모집이 끝났고 중단되지 않았다(결정 31) */
    public static final Set<GroupBuyStatus> ROUND_SOURCE = EnumSet.of(CLOSING, CLOSED_SUCCESS, CLOSED_FAILED, ORDERED, SHIPPING, COMPLETED);

    /** 결제 확정을 받는 상태 — 마감 전에 잡힌 선점의 결제는 CLOSING 에도 받는다(commerce.md 8-6) */
    public static final Set<GroupBuyStatus> ACCEPTS_PAYMENT = EnumSet.of(RECRUITING, CLOSING);
}

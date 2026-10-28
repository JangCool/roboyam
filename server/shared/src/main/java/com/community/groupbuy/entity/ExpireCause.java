package com.community.groupbuy.entity;

/**
 * 주문 만료 원인({@code group_buy_participations.expire_cause} · 상태 이력 {@code cause}, 결정 28·62). 선점 반복 이탈(공구 참여 제한)은 {@link #HOLD_TIMEOUT} 만 센다.
 */
public enum ExpireCause {
    /** 선점 시간 초과 — 이탈 횟수에 센다 */
    HOLD_TIMEOUT,
    /** 공구 중단으로 결제 중 주문을 닫음(결정 29 ①) */
    GROUP_BUY_CANCELED,
    /** 공구가 마감·판정돼 결제 중 주문을 닫음 */
    PAYMENT_FAILED_CLOSED,
    /** 결제는 왔지만 공구가 이미 결제를 받지 않는 상태(판정 뒤 — P3b) */
    REJECTED_CLOSED,
    /** 결제가 선점 시간 뒤에 됨 */
    LATE_PAYMENT,
    /** 결제 확정 때 수량 불변식이 깨져 있었음(결정 82) · 금액 불일치 */
    REJECTED_ANOMALY
}

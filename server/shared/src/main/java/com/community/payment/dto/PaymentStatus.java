package com.community.payment.dto;

/** 결제 주문 상태({@code payment_orders.status}) — 전이는 저장소의 조건부 UPDATE 로만 한다 */
public enum PaymentStatus {
    /** 결제 대기 — 서버가 만들었고 아직 결제가 확인되지 않았다 */
    READY,
    PAID,
    PARTIAL_CANCELED,
    CANCELED,
    /** 유효 시각이 지났거나 재결제로 닫혔다 — 늦게 결제되면 PAID + {@link PaymentAnomaly#LATE_PAYMENT} */
    EXPIRED;

    /** 결제된 계열인가(PAID·PARTIAL_CANCELED·CANCELED) */
    public boolean paid() {
        return this == PAID || this == PARTIAL_CANCELED || this == CANCELED;
    }
}

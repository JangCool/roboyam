package com.community.payment.gateway;

/**
 * 포트원 결제 건 상태({@code Payment.status} — V2 OpenAPI 의 판별자 값) + 우리가 붙인 두 값. 확정은 {@link #PAID} 만 받아들인다(commerce.md 8-5 — 비동기 결제·가상계좌는 다루지 않는다).
 */
public enum PgPaymentStatus {
    READY,
    PENDING,
    PAY_PENDING,
    VIRTUAL_ACCOUNT_ISSUED,
    PAID,
    FAILED,
    PARTIAL_CANCELLED,
    CANCELLED,
    /** 포트원에 그 결제 건이 없다(PAYMENT_NOT_FOUND) — 결제창을 열지 않았다 */
    NOT_FOUND,
    /** 우리가 모르는 상태 값 — 결제로 보지 않는다 */
    UNKNOWN;

    public static PgPaymentStatus parse(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (PgPaymentStatus status : values()) {
            if (status.name().equals(value) && status != NOT_FOUND && status != UNKNOWN) {
                return status;
            }
        }
        return UNKNOWN;
    }
}

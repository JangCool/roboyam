package com.community.payment.dto;

/** 결제를 확정한 길({@code payment_orders.confirmed_via}) — 네 길이 모두 같은 확정 코드({@code PaymentConfirmService.confirm})를 쓴다 */
public enum ConfirmedVia {
    COMPLETE_API,
    WEBHOOK,
    READY_CLEANUP,
    ADMIN_RECHECK
}

package com.community.payment.dto;

/** 포트원 취소 내역 상태({@code PaymentCancellation.status}) — {@code payment_cancels.pg_status} */
public enum PgCancelStatus {
    /** 접수만 됐다 — 끝난 것이 아니라 재시도 전에 조회로 확인한다 */
    REQUESTED,
    SUCCEEDED,
    FAILED
}

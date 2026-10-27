package com.community.payment.dto;

/** 취소 대기열의 우리 상태({@code payment_cancels.status}, 결정 2) — 포트원 응답은 {@link PgCancelStatus} 로 따로 둔다 */
public enum CancelStatus {
    PENDING,
    SENDING,
    SUCCEEDED,
    /** 재시도를 다 썼다 — 결제 이상 목록·운영자 알림 */
    GAVE_UP,
    /** 운영자가 수동 처리 완료로 닫았다(2a-3) */
    MANUAL
}

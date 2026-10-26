package com.community.payment.dto;

/** 취소 종류({@code payment_cancels.kind}) */
public enum CancelKind {
    /** 회원의 취소(공구 성사 전 취소 등) */
    USER_CANCEL,
    /** 환불(반품·불발 등) */
    REFUND,
    /** 자동 취소 — 늦은 결제·금액 불일치·도메인 거절(D10) */
    AUTO_REJECT,
    /** 운영자 취소 */
    ADMIN,
    /** 충전 환불(3단계) */
    CHARGE_REFUND
}

package com.community.groupbuy.entity;

import com.community.payment.dto.CancelKind;

/** 환불 원인({@code group_buy_refunds.cause}, 결정 36·53·52·68) */
public enum RefundCause {
    REQUEST, USER_CANCEL, FAILED, CANCELED, ADMIN, SUPPLY_FAILURE, DELAY, RETURN_FEE_REFUND;

    /** 결제 취소 종류(결정 36 — 본인 취소 USER_CANCEL · 불발·중단·반품 REFUND · 운영 환불 ADMIN) */
    public CancelKind cancelKind() {
        return switch (this) {
            case USER_CANCEL, DELAY -> CancelKind.USER_CANCEL;
            case ADMIN -> CancelKind.ADMIN;
            default -> CancelKind.REFUND;
        };
    }
}

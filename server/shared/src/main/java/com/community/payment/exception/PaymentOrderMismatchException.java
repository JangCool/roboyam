package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 그 회원의 결제지만 다른 주문의 결제다(P24 — 포트원 조회 없이 거절) */
public class PaymentOrderMismatchException extends CommonException {

    public PaymentOrderMismatchException() {
        super(HttpStatus.CONFLICT, "PAYMENT_ORDER_MISMATCH", new MessageRef("error.PAYMENT_ORDER_MISMATCH"));
    }
}

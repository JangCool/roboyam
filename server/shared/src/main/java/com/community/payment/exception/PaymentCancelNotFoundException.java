package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그런 결제 취소 줄이 없다 */
public class PaymentCancelNotFoundException extends CommonException {

    public PaymentCancelNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PAYMENT_CANCEL_NOT_FOUND", new MessageRef("error.PAYMENT_CANCEL_NOT_FOUND"));
    }
}

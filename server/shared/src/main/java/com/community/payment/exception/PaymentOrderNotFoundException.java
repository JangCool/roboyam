package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그런 결제가 없거나 다른 회원의 결제다(있는지 알리지 않는다, P24) */
public class PaymentOrderNotFoundException extends CommonException {

    public PaymentOrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", new MessageRef("error.PAYMENT_ORDER_NOT_FOUND"));
    }
}

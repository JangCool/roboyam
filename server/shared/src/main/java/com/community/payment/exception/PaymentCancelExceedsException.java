package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 취소 금액이 남은 취소 가능 금액(결제 − 취소됨 − 진행 중·멈춘 취소)을 넘거나 결제되지 않은 주문이다 */
public class PaymentCancelExceedsException extends CommonException {

    public PaymentCancelExceedsException() {
        super(HttpStatus.CONFLICT, "PAYMENT_CANCEL_EXCEEDS", new MessageRef("error.PAYMENT_CANCEL_EXCEEDS"));
    }
}

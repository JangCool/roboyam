package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 포트원이 아직 결제 완료가 아니라고 한다(결제 대기·실패·없음). 결제 주문은 그대로 READY 라 다시 시도할 수 있다(commerce.md 8-2) */
public class PaymentNotPaidException extends CommonException {

    public PaymentNotPaidException() {
        super(HttpStatus.CONFLICT, "PAYMENT_NOT_PAID", new MessageRef("error.PAYMENT_NOT_PAID"));
    }
}

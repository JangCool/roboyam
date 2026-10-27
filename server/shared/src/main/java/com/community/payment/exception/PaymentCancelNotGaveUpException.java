package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 멈춘(GAVE_UP) 취소가 아니다(이미 다시 시도·수동 처리됐거나 진행 중) — 「다시 시도」·「수동 처리 완료」는 GAVE_UP 에만 */
public class PaymentCancelNotGaveUpException extends CommonException {

    public PaymentCancelNotGaveUpException() {
        super(HttpStatus.CONFLICT, "PAYMENT_CANCEL_NOT_GAVE_UP", new MessageRef("error.PAYMENT_CANCEL_NOT_GAVE_UP"));
    }
}

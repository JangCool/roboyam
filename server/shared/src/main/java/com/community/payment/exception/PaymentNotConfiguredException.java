package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 503 — 포트원 결제 설정(상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키) 중 빈 칸이 있다. 앱은 뜨고 결제 시작만 막힌다(commerce.md 8-5) */
public class PaymentNotConfiguredException extends CommonException {

    public PaymentNotConfiguredException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED", new MessageRef("error.PAYMENT_NOT_CONFIGURED"));
    }
}

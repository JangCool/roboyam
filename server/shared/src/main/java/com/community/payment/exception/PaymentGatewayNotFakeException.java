package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 가짜 결제창({@code /api/dev/payments/{paymentId}/fake-checkout})을 가짜 게이트웨이가 아닌 서버(포트원)에서 불렀다 */
public class PaymentGatewayNotFakeException extends CommonException {

    public PaymentGatewayNotFakeException() {
        super(HttpStatus.CONFLICT, "PAYMENT_GATEWAY_NOT_FAKE", new MessageRef("error.PAYMENT_GATEWAY_NOT_FAKE"));
    }
}

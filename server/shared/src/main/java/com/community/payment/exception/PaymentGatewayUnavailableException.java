package com.community.payment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 503 — 포트원에 연결하지 못했거나 응답을 믿을 수 없다(연결 실패·5xx·모양이 틀림·우리 결제가 아님). 다시 시도할 수 있다 */
public class PaymentGatewayUnavailableException extends CommonException {

    public PaymentGatewayUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_UNAVAILABLE", new MessageRef("error.PAYMENT_GATEWAY_UNAVAILABLE"));
    }
}

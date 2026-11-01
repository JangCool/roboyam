package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 자기 딜에는 종료 요청을 보낼 수 없다(403) */
public class DealEndRequestOwnException extends CommonException {

    public DealEndRequestOwnException() {
        super(HttpStatus.FORBIDDEN, "DEAL_END_REQUEST_OWN", new MessageRef("error.DEAL_END_REQUEST_OWN"));
    }
}

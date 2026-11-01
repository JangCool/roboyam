package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 딜에 이미 종료 요청을 보냈다(409) — 하루·작성자 한도를 쓰지 않는다 */
public class DealEndRequestExistsException extends CommonException {

    public DealEndRequestExistsException() {
        super(HttpStatus.CONFLICT, "DEAL_END_REQUEST_EXISTS", new MessageRef("error.DEAL_END_REQUEST_EXISTS"));
    }
}

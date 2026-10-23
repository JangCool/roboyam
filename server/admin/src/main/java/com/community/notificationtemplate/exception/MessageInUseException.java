package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 쓰는 중인 문구(언어별 쓸 문구 · 특수 상황 규칙)를 지우려 했다(409) */
public class MessageInUseException extends CommonException {

    public MessageInUseException() {
        super(HttpStatus.CONFLICT, "MESSAGE_IN_USE", new MessageRef("error.MESSAGE_IN_USE"));
    }
}

package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 한 대상·채널의 사용자 문구가 상한에 닿았다(409) */
public class MessageLimitException extends CommonException {

    public MessageLimitException(int limit) {
        super(HttpStatus.CONFLICT, "MESSAGE_LIMIT", new MessageRef("error.MESSAGE_LIMIT", String.valueOf(limit)));
    }
}

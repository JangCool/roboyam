package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 기본 문구(코드가 준다)를 고치거나 지우려 했다(409) — 복사해서 만든다 */
public class MessageBuiltinException extends CommonException {

    public MessageBuiltinException() {
        super(HttpStatus.CONFLICT, "MESSAGE_BUILTIN", new MessageRef("error.MESSAGE_BUILTIN"));
    }
}

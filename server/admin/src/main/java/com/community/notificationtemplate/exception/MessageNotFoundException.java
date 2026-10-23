package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 문구가 없다(404) — 모르는 id, 지워진 문구 */
public class MessageNotFoundException extends CommonException {

    public MessageNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", new MessageRef("error.MESSAGE_NOT_FOUND"));
    }
}

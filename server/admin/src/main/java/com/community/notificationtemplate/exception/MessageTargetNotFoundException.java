package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 문구 대상(이벤트 · 필수 안내 메일)이 카탈로그에 없다(404) */
public class MessageTargetNotFoundException extends CommonException {

    public MessageTargetNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MESSAGE_TARGET_NOT_FOUND", new MessageRef("error.MESSAGE_TARGET_NOT_FOUND"));
    }
}

package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 종류의 사용자 템플릿이 상한에 닿았다(409) */
public class TemplateLimitException extends CommonException {

    public TemplateLimitException(int limit) {
        super(HttpStatus.CONFLICT, "TEMPLATE_LIMIT", new MessageRef("error.TEMPLATE_LIMIT", String.valueOf(limit)));
    }
}

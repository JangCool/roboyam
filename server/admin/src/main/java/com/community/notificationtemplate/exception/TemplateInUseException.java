package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 쓰는 문구가 있는 템플릿을 지우려 했다(409) */
public class TemplateInUseException extends CommonException {

    public TemplateInUseException(int count) {
        super(HttpStatus.CONFLICT, "TEMPLATE_IN_USE", new MessageRef("error.TEMPLATE_IN_USE", String.valueOf(count)));
    }
}

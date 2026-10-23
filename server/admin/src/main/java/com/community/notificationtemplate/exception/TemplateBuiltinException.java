package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 기본 템플릿(코드가 준다)을 고치거나 지우려 했다(409) — 복사해서 만든다 */
public class TemplateBuiltinException extends CommonException {

    public TemplateBuiltinException() {
        super(HttpStatus.CONFLICT, "TEMPLATE_BUILTIN", new MessageRef("error.TEMPLATE_BUILTIN"));
    }
}

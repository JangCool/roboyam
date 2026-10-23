package com.community.notificationtemplate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 템플릿(메일 · Slack · Mattermost · 카카오톡)이 없다(404) — 모르는 id, 다른 종류의 id */
public class TemplateNotFoundException extends CommonException {

    public TemplateNotFoundException() {
        super(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", new MessageRef("error.TEMPLATE_NOT_FOUND"));
    }
}

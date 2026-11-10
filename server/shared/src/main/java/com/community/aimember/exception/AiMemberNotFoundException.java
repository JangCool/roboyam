package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** AI 회원이 없다(404) — 코드 {@code AI_MEMBER_NOT_FOUND} */
public class AiMemberNotFoundException extends CommonException {

    public AiMemberNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AI_MEMBER_NOT_FOUND", new MessageRef("error.AI_MEMBER_NOT_FOUND"));
    }
}

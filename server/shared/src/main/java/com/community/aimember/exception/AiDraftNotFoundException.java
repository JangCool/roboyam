package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 생성물이 없거나 이미 처리됐다(404) — 코드 {@code AI_DRAFT_NOT_FOUND} */
public class AiDraftNotFoundException extends CommonException {

    public AiDraftNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AI_DRAFT_NOT_FOUND", new MessageRef("error.AI_DRAFT_NOT_FOUND"));
    }
}

package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 모델 목록에 그 줄이 없다(404) — 코드 {@code AI_MODEL_NOT_FOUND} */
public class AiModelNotFoundException extends CommonException {

    public AiModelNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AI_MODEL_NOT_FOUND", new MessageRef("error.AI_MODEL_NOT_FOUND"));
    }
}

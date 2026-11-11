package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 모델을 쓰는 AI 계정이 있어 지울 수 없다(409) — 대신 「사용」을 끈다. 코드 {@code AI_MODEL_IN_USE} */
public class AiModelInUseException extends CommonException {

    public AiModelInUseException() {
        super(HttpStatus.CONFLICT, "AI_MODEL_IN_USE", new MessageRef("error.AI_MODEL_IN_USE"));
    }
}

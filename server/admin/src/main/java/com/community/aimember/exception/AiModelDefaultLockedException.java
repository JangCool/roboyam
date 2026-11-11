package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 기본 모델은 끄거나 지울 수 없다(409) — 다른 모델을 기본으로 정한 뒤 바꾼다. 코드 {@code AI_MODEL_DEFAULT_LOCKED} */
public class AiModelDefaultLockedException extends CommonException {

    public AiModelDefaultLockedException() {
        super(HttpStatus.CONFLICT, "AI_MODEL_DEFAULT_LOCKED", new MessageRef("error.AI_MODEL_DEFAULT_LOCKED"));
    }
}

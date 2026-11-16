package com.community.aimember.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 뉴스 출처 줄이 없다(404) — 코드 {@code AI_NEWS_SOURCE_NOT_FOUND} */
public class AiNewsSourceNotFoundException extends CommonException {

    public AiNewsSourceNotFoundException() {
        super(HttpStatus.NOT_FOUND, "AI_NEWS_SOURCE_NOT_FOUND", new MessageRef("error.AI_NEWS_SOURCE_NOT_FOUND"));
    }
}

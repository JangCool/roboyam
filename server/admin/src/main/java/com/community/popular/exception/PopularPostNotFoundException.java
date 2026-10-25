package com.community.popular.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 인기글에서 뺄 글이 없거나 지워졌다(404) — 공개 사이트와 같은 코드 {@code POST_NOT_FOUND} */
public class PopularPostNotFoundException extends CommonException {

    public PopularPostNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", new MessageRef("error.POST_NOT_FOUND"));
    }
}

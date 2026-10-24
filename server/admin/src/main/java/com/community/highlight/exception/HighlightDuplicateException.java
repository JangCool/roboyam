package com.community.highlight.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 하이라이트로 등록된 글이다(409) */
public class HighlightDuplicateException extends CommonException {

    public HighlightDuplicateException() {
        super(HttpStatus.CONFLICT, "HIGHLIGHT_DUPLICATE", new MessageRef("error.HIGHLIGHT_DUPLICATE"));
    }
}

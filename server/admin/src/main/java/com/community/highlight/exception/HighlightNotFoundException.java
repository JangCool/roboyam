package com.community.highlight.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 하이라이트다(404) */
public class HighlightNotFoundException extends CommonException {

    public HighlightNotFoundException() {
        super(HttpStatus.NOT_FOUND, "HIGHLIGHT_NOT_FOUND", new MessageRef("error.HIGHLIGHT_NOT_FOUND"));
    }
}

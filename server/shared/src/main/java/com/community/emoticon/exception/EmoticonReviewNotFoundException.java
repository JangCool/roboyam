package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 심사 건이 없다(형식이 틀린 ID 포함) */
public class EmoticonReviewNotFoundException extends CommonException {

    public EmoticonReviewNotFoundException() {
        super(HttpStatus.NOT_FOUND, "EMOTICON_REVIEW_NOT_FOUND", new MessageRef("error.EMOTICON_REVIEW_NOT_FOUND"));
    }
}

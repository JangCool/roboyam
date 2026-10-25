package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내 댓글은 비추천할 수 없음(400) */
public class CannotDislikeOwnException extends CommonException {

    public CannotDislikeOwnException() {
        super(HttpStatus.BAD_REQUEST, "CANNOT_DISLIKE_OWN", new MessageRef("error.CANNOT_DISLIKE_OWN"));
    }
}

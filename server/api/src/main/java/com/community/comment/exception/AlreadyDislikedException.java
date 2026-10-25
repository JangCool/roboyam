package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 비추천한 댓글(409) — 취소는 DELETE …/dislike */
public class AlreadyDislikedException extends CommonException {

    public AlreadyDislikedException() {
        super(HttpStatus.CONFLICT, "ALREADY_DISLIKED", new MessageRef("error.ALREADY_DISLIKED"));
    }
}

package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고로 숨겨진 글을 직접 열거나 고치려 함(403) */
public class PostHiddenException extends CommonException {

    public PostHiddenException() {
        super(HttpStatus.FORBIDDEN, "POST_HIDDEN", new MessageRef("error.POST_HIDDEN"));
    }
}

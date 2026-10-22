package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없거나 지운 댓글(404) */
public class CommentNotFoundException extends CommonException {

    public CommentNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", new MessageRef("error.COMMENT_NOT_FOUND"));
    }
}

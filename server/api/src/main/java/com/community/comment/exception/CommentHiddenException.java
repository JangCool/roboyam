package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 신고로 숨겨진 댓글을 고치거나 추천하려 함(403) */
public class CommentHiddenException extends CommonException {

    public CommentHiddenException() {
        super(HttpStatus.FORBIDDEN, "COMMENT_HIDDEN", new MessageRef("error.COMMENT_HIDDEN"));
    }
}

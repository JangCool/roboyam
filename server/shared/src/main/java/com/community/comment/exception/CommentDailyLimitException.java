package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 최근 24시간 댓글 수 한도를 넘음(429) */
public class CommentDailyLimitException extends CommonException {

    public CommentDailyLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "COMMENT_DAILY_LIMIT", new MessageRef("error.COMMENT_DAILY_LIMIT", String.valueOf(limit)));
    }
}

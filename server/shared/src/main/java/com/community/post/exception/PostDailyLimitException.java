package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 최근 24시간 글 수 한도를 넘음(429) */
public class PostDailyLimitException extends CommonException {

    public PostDailyLimitException(int limit) {
        super(HttpStatus.TOO_MANY_REQUESTS, "POST_DAILY_LIMIT", new MessageRef("error.POST_DAILY_LIMIT", String.valueOf(limit)));
    }
}

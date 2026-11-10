package com.community.comment.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 작성 간격(게시판 기본 설정) 안에 다시 씀(429) — 기다릴 초를 {@code retryAfterSeconds} 로 준다 */
public class CommentTooFastException extends CommonException {

    public CommentTooFastException(int gapSeconds, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "COMMENT_TOO_FAST", new MessageRef("error.COMMENT_TOO_FAST", gapSeconds, retryAfterSeconds), Map.of(), retryAfterSeconds);
    }
}

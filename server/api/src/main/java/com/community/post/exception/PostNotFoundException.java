package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없거나 지운 글(404) — 예약 삭제 시각이 지난 글·임시저장도 같다 */
public class PostNotFoundException extends CommonException {

    public PostNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", new MessageRef("error.POST_NOT_FOUND"));
    }
}

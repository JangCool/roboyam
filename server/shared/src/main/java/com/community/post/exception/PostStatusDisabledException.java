package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 처리 상태를 쓰지 않는 게시판의 글 상태를 바꾸려 함(409 POST_STATUS_DISABLED — V166 운영 게시판 설정 {@code statusEnabled}) */
public class PostStatusDisabledException extends CommonException {

    public PostStatusDisabledException() {
        super(HttpStatus.CONFLICT, "POST_STATUS_DISABLED", new MessageRef("error.POST_STATUS_DISABLED"));
    }
}

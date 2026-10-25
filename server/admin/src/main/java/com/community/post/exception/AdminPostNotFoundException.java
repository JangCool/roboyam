package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 게시글 관리 — 없는 글·형식이 틀린 번호(404 POST_NOT_FOUND) */
public class AdminPostNotFoundException extends CommonException {

    public AdminPostNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", new MessageRef("error.POST_NOT_FOUND"));
    }
}

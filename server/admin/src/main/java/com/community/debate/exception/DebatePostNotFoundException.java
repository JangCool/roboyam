package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 토론을 다룰 글이 없다(404 POST_NOT_FOUND) — 형식이 틀린 번호·없는 글(의견 관리), 지운 글(토론 열기) */
public class DebatePostNotFoundException extends CommonException {

    public DebatePostNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", new MessageRef("error.POST_NOT_FOUND"));
    }
}

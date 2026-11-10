package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 익명 글·댓글이 아니다(작성자 열람 · 「이 글 작성자」 제재는 익명 글·댓글에만 — anonymous-board-contract 「API 계약」) */
public class NotAnonymousContentException extends CommonException {

    public NotAnonymousContentException() {
        super(HttpStatus.CONFLICT, "NOT_ANONYMOUS", new MessageRef("error.NOT_ANONYMOUS"));
    }
}

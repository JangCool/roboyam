package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 남의 회원 글·댓글을 고치거나 지우려 함(403 {@code FORBIDDEN}) — 회원 글은 쓴 회원만, 비회원 글은 비밀번호로 한다 */
public class NotAuthorException extends CommonException {

    public NotAuthorException() {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", new MessageRef("error.FORBIDDEN.notAuthor"));
    }
}

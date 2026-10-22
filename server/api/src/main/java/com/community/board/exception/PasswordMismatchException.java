package com.community.board.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 비회원 글·댓글의 비밀번호가 맞지 않음(403) — 비밀번호가 없거나 비회원 글이 아닐 때도 같다 */
public class PasswordMismatchException extends CommonException {

    public PasswordMismatchException() {
        super(HttpStatus.FORBIDDEN, "PASSWORD_MISMATCH", new MessageRef("error.PASSWORD_MISMATCH"));
    }
}

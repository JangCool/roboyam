package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 새 비밀번호가 지금 비밀번호와 같다(400 {@code SAME_PASSWORD}) — 비밀번호 재설정·변경 */
public class AuthSamePasswordException extends CommonException {

    public AuthSamePasswordException() {
        super(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", new MessageRef("error.SAME_PASSWORD"));
    }
}

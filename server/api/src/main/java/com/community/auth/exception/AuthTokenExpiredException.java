package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 메일 링크의 유효 시간이 지났다(410 {@code TOKEN_EXPIRED}) — 새 링크를 받아야 한다 */
public class AuthTokenExpiredException extends CommonException {

    public AuthTokenExpiredException() {
        super(HttpStatus.GONE, "TOKEN_EXPIRED", new MessageRef("error.TOKEN_EXPIRED"));
    }
}

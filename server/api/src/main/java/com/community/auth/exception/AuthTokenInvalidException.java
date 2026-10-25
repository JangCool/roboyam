package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 메일 링크(이메일 인증·이메일 변경·비밀번호 재설정)를 쓸 수 없다(400 {@code TOKEN_INVALID}) — 없는 토큰·이미 쓴 토큰·다른 용도의 토큰·탈퇴한 회원의 토큰을 구분하지 않는다 */
public class AuthTokenInvalidException extends CommonException {

    public AuthTokenInvalidException() {
        super(HttpStatus.BAD_REQUEST, "TOKEN_INVALID", new MessageRef("error.TOKEN_INVALID"));
    }
}

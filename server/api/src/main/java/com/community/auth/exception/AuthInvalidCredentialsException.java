package com.community.auth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이메일 또는 비밀번호가 맞지 않는다(401 {@code INVALID_CREDENTIALS}). 이메일이 없든, 비밀번호가 틀리든, 소셜로만 가입한 계정이든, 탈퇴한 계정이든
 * <b>같은 응답</b>이다 — 어느 쪽이 틀렸는지, 계정이 있는지 알리지 않는다. 문구에 이메일을 싣지 않는다.
 */
public class AuthInvalidCredentialsException extends CommonException {

    public AuthInvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", new MessageRef("error.INVALID_CREDENTIALS"));
    }
}

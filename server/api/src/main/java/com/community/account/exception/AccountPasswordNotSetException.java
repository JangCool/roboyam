package com.community.account.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 비밀번호가 없는(소셜로만 가입한) 계정이 비밀번호를 바꾸려 한다(409 {@code PASSWORD_NOT_SET}) */
public class AccountPasswordNotSetException extends CommonException {

    public AccountPasswordNotSetException() {
        super(HttpStatus.CONFLICT, "PASSWORD_NOT_SET", new MessageRef("error.PASSWORD_NOT_SET"));
    }
}

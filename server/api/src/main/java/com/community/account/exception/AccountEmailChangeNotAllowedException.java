package com.community.account.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이메일을 바꿀 수 없는 계정이다(409 {@code EMAIL_CHANGE_NOT_ALLOWED}) — 비밀번호가 없는(소셜로만 가입한) 계정은 본인 확인을 할 수 없다 */
public class AccountEmailChangeNotAllowedException extends CommonException {

    public AccountEmailChangeNotAllowedException() {
        super(HttpStatus.CONFLICT, "EMAIL_CHANGE_NOT_ALLOWED", new MessageRef("error.EMAIL_CHANGE_NOT_ALLOWED"));
    }
}

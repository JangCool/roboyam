package com.community.account.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 현재 비밀번호가 맞지 않는다(401 {@code CURRENT_PASSWORD_WRONG}) — 비밀번호 변경·이메일 변경의 본인 확인. 로그인 실패와 같은 횟수에 센다 */
public class AccountCurrentPasswordWrongException extends CommonException {

    public AccountCurrentPasswordWrongException() {
        super(HttpStatus.UNAUTHORIZED, "CURRENT_PASSWORD_WRONG", new MessageRef("error.CURRENT_PASSWORD_WRONG"));
    }
}

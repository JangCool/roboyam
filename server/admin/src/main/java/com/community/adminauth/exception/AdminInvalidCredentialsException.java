package com.community.adminauth.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 이메일·비밀번호가 맞지 않는다(401). 없는 계정 · 틀린 비밀번호 · 승인 전·잠김·만료·사용 중지·기간 밖의 계정을 <b>밖으로 구분해 알리지 않고</b> 모두 이것이다
 * (계정이 있는지·막혔는지 응답으로 알 수 없게).
 */
public class AdminInvalidCredentialsException extends CommonException {

    public AdminInvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", new MessageRef("error.INVALID_CREDENTIALS"));
    }
}

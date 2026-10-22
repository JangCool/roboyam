package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 비밀번호가 없는 소셜 전용 회원의 마지막 연결은 해제할 수 없다(409). */
public class SocialLastLoginMethodException extends CommonException {

    public SocialLastLoginMethodException() {
        super(HttpStatus.CONFLICT, "SOCIAL_LAST_LOGIN_METHOD", new MessageRef("error.SOCIAL_LAST_LOGIN_METHOD"));
    }
}

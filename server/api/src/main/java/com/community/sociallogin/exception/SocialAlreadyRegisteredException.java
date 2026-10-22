package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 가입을 마치는 사이 그 소셜 계정이 이미 회원에 연결됐다(409, 두 번 누름·다른 탭) — 화면은 로그인 화면으로 돌려보낸다. */
public class SocialAlreadyRegisteredException extends CommonException {

    public SocialAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "SOCIAL_ALREADY_REGISTERED", new MessageRef("error.SOCIAL_ALREADY_REGISTERED"));
    }
}

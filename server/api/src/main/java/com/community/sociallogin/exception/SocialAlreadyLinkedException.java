package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 회원에게 그 공급자의 계정이 이미 연결돼 있다(409). */
public class SocialAlreadyLinkedException extends CommonException {

    public SocialAlreadyLinkedException() {
        super(HttpStatus.CONFLICT, "SOCIAL_ALREADY_LINKED", new MessageRef("error.SOCIAL_ALREADY_LINKED"));
    }
}

package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 회원에게 그 공급자가 연결돼 있지 않다(404). */
public class SocialNotLinkedException extends CommonException {

    public SocialNotLinkedException() {
        super(HttpStatus.NOT_FOUND, "SOCIAL_NOT_LINKED", new MessageRef("error.SOCIAL_NOT_LINKED"));
    }
}

package com.community.sociallogin.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이어갈 소셜 가입이 없다 — 세션이 없거나 만료됐거나 이미 끝냈다(404). 화면은 로그인 화면으로 돌려보낸다. */
public class SocialPendingNotFoundException extends CommonException {

    public SocialPendingNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NO_PENDING", new MessageRef("error.NO_PENDING"));
    }
}

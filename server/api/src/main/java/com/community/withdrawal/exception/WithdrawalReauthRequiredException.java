package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 소셜 전용 회원의 본인 확인 — 최근(설정 분) 안 로그인이 아니다(401 {@code REAUTH_REQUIRED}). 화면은 다시 로그인으로 보낸다 */
public class WithdrawalReauthRequiredException extends CommonException {

    public WithdrawalReauthRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "REAUTH_REQUIRED", new MessageRef("error.REAUTH_REQUIRED"));
    }
}

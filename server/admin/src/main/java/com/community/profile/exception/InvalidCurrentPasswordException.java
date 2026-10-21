package com.community.profile.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 현재 비밀번호가 맞지 않다(<b>400</b> {@code INVALID_CURRENT_PASSWORD}). 401 이 아니다 — 공통 클라이언트는 로그인 상태에서의 401 을 「세션이 끝났다」로 보고 로그인 화면으로 보낸다.
 */
public class InvalidCurrentPasswordException extends CommonException {

    public InvalidCurrentPasswordException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_PASSWORD", new MessageRef("error.INVALID_CURRENT_PASSWORD"));
    }
}

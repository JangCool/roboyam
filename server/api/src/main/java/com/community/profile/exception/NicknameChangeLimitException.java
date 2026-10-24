package com.community.profile.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 가입 뒤 바꿀 수 있는 닉네임 변경 횟수를 이미 썼다(409) — 닉네임 변경권이 생기면 {@code NICKNAME_TICKET_REQUIRED} 로 바뀐다(계약 「후속 작업」) */
public class NicknameChangeLimitException extends CommonException {

    public NicknameChangeLimitException(int freeLimit) {
        super(HttpStatus.CONFLICT, "NICKNAME_CHANGE_LIMIT", new MessageRef("error.NICKNAME_CHANGE_LIMIT", freeLimit));
    }
}

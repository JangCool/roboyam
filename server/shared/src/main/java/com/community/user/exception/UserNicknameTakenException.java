package com.community.user.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 그 닉네임을 쓰는 회원이 있다(409). 문구에 닉네임을 싣지 않는다. */
public class UserNicknameTakenException extends CommonException {

    public UserNicknameTakenException() {
        super(HttpStatus.CONFLICT, "NICKNAME_TAKEN", new MessageRef("error.NICKNAME_TAKEN"));
    }
}

package com.community.user.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 그 이메일의 회원이 있다(409). 문구에 이메일을 싣지 않는다. */
public class UserEmailTakenException extends CommonException {

    public UserEmailTakenException() {
        super(HttpStatus.CONFLICT, "EMAIL_TAKEN", new MessageRef("error.EMAIL_TAKEN"));
    }
}

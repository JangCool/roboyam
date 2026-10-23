package com.community.membernotification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 회원 이벤트다(404) */
public class MemberEventNotFoundException extends CommonException {

    public MemberEventNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MEMBER_EVENT_NOT_FOUND", new MessageRef("error.MEMBER_EVENT_NOT_FOUND"));
    }
}

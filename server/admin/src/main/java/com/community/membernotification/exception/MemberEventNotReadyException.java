package com.community.membernotification.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 아직 일어나지 않는(준비 중) 회원 이벤트는 설정을 바꿀 수 없다(409) */
public class MemberEventNotReadyException extends CommonException {

    public MemberEventNotReadyException() {
        super(HttpStatus.CONFLICT, "MEMBER_EVENT_NOT_READY", new MessageRef("error.MEMBER_EVENT_NOT_READY"));
    }
}

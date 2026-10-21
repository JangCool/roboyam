package com.community.member.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 회원이다(404). 번호 형식이 틀려도 같다 — 어떤 번호가 있는지 알리지 않는다. */
public class MemberNotFoundException extends CommonException {

    public MemberNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", new MessageRef("error.MEMBER_NOT_FOUND"));
    }
}

package com.community.restriction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 회원(관리자 회원 화면의 MEMBER_NOT_FOUND 와 같은 코드·문구) */
public class RestrictionMemberNotFoundException extends CommonException {

    public RestrictionMemberNotFoundException() {
        super(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", new MessageRef("error.MEMBER_NOT_FOUND"));
    }
}

package com.community.restriction.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 탈퇴한 회원에게는 기능 제한을 걸 수 없다(화면 확정 결정 3) */
public class MemberWithdrawnException extends CommonException {

    public MemberWithdrawnException() {
        super(HttpStatus.CONFLICT, "MEMBER_WITHDRAWN", new MessageRef("error.MEMBER_WITHDRAWN"));
    }
}

package com.community.withdrawal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 분리 보관한 탈퇴 회원 연락처가 없다(결제 이력이 없던 회원이거나 파기됐다) */
public class WithdrawnContactNotFoundException extends CommonException {

    public WithdrawnContactNotFoundException() {
        super(HttpStatus.NOT_FOUND, "WITHDRAWN_CONTACT_NOT_FOUND", new MessageRef("error.WITHDRAWN_CONTACT_NOT_FOUND"));
    }
}

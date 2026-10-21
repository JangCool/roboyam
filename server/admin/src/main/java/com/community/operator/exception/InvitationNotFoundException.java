package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는(이미 취소됐거나 수락된) 초대다(404). */
public class InvitationNotFoundException extends CommonException {

    public InvitationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", new MessageRef("error.INVITATION_NOT_FOUND"));
    }
}

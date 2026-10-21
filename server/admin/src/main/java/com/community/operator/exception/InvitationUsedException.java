package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 수락한 초대다(409 {@code INVITATION_USED}) */
public class InvitationUsedException extends CommonException {

    public InvitationUsedException() {
        super(HttpStatus.CONFLICT, "INVITATION_USED", new MessageRef("error.INVITATION_USED"));
    }
}

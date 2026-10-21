package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 초대 링크의 유효 시간이 지났다(410 {@code INVITATION_EXPIRED}) */
public class InvitationExpiredException extends CommonException {

    public InvitationExpiredException() {
        super(HttpStatus.GONE, "INVITATION_EXPIRED", new MessageRef("error.INVITATION_EXPIRED"));
    }
}

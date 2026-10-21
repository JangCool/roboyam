package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 이메일로 유효한 초대가 이미 있다(409). 취소한 뒤 다시 보내야 한다. */
public class InvitationDuplicatedException extends CommonException {

    public InvitationDuplicatedException() {
        super(HttpStatus.CONFLICT, "INVITATION_DUPLICATED", new MessageRef("error.INVITATION_DUPLICATED"), Map.of("email", new MessageRef("operator.invitation.duplicated")), null);
    }
}

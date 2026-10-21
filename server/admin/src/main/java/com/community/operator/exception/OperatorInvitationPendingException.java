package com.community.operator.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 이메일로 보낸 초대가 아직 유효해(만료 전·미수락) 운영자를 바로 추가할 수 없다(409). 초대를 취소한 뒤 다시 추가해야 한다. 이메일 칸에 문구가 붙는다. */
public class OperatorInvitationPendingException extends CommonException {

    public OperatorInvitationPendingException() {
        super(HttpStatus.CONFLICT, "OPERATOR_INVITATION_PENDING", new MessageRef("error.OPERATOR_INVITATION_PENDING"),
                Map.of("email", new MessageRef("operator.invitation.pending")), null);
    }
}

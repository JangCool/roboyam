package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 팀원으로 보낸 운영자가 없거나 새로 넣을 수 없는 계정(지금 활성이 아님)이다(400). {@code fields.operatorIds} */
public class TeamOperatorNotFoundException extends CommonException {

    public TeamOperatorNotFoundException() {
        super(HttpStatus.BAD_REQUEST, "TEAM_OPERATOR_NOT_FOUND", new MessageRef("error.TEAM_OPERATOR_NOT_FOUND"),
                Map.of("operatorIds", new MessageRef("error.TEAM_OPERATOR_NOT_FOUND")), null);
    }
}

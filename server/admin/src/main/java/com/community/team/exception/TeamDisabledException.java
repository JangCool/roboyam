package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 사용 안 함인 팀을 알림 이벤트의 추가 수신 팀으로 새로 골랐다(409) — 이미 골라 둔 팀을 그대로 두는 것은 허용한다. {@code fields.teamIds} */
public class TeamDisabledException extends CommonException {

    public TeamDisabledException() {
        super(HttpStatus.CONFLICT, "TEAM_DISABLED", new MessageRef("error.TEAM_DISABLED"), Map.of("teamIds", new MessageRef("error.TEAM_DISABLED")), null);
    }
}

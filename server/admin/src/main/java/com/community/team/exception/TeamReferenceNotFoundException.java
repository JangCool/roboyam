package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 알림 이벤트의 추가 수신 팀으로 보낸 팀이 없다(400, 코드는 {@code TEAM_NOT_FOUND}) — 요청 대상이 아니라 요청 안의 값이 틀린 것이라 404 가 아니다. {@code fields.teamIds} */
public class TeamReferenceNotFoundException extends CommonException {

    public TeamReferenceNotFoundException() {
        super(HttpStatus.BAD_REQUEST, "TEAM_NOT_FOUND", new MessageRef("error.TEAM_NOT_FOUND"), Map.of("teamIds", new MessageRef("error.TEAM_NOT_FOUND")), null);
    }
}

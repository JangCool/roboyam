package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 팀원이 있는 팀은 지울 수 없다(409) — 대신 「사용 안 함」으로 멈춘다 */
public class TeamHasMembersException extends CommonException {

    public TeamHasMembersException() {
        super(HttpStatus.CONFLICT, "TEAM_HAS_MEMBERS", new MessageRef("error.TEAM_HAS_MEMBERS"));
    }
}

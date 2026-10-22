package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 팀을 더 만들 수 없다(409) — 팀 수 상한. 한도는 문구 인자로 알린다 */
public class TeamLimitExceededException extends CommonException {

    public TeamLimitExceededException(int limit) {
        super(HttpStatus.CONFLICT, "TEAM_LIMIT_EXCEEDED", new MessageRef("error.TEAM_LIMIT_EXCEEDED", String.valueOf(limit)));
    }
}

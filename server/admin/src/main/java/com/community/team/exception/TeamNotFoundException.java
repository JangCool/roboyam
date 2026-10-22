package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 없는 팀이다(404) */
public class TeamNotFoundException extends CommonException {

    public TeamNotFoundException() {
        super(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", new MessageRef("error.TEAM_NOT_FOUND"));
    }
}

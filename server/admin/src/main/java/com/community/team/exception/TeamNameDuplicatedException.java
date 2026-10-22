package com.community.team.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 같은 이름의 팀이 이미 있다(409, 대소문자 무시). 이름 칸({@code teamName})에 문구가 붙는다 */
public class TeamNameDuplicatedException extends CommonException {

    public TeamNameDuplicatedException() {
        super(HttpStatus.CONFLICT, "TEAM_NAME_DUPLICATED", new MessageRef("error.TEAM_NAME_DUPLICATED"),
                Map.of("teamName", new MessageRef("error.TEAM_NAME_DUPLICATED")), null);
    }
}

package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 모임장(체크형 일은 맡은 매니저)만 할 수 있다(403) — 코드 {@code GROUP_LEADER_ONLY} */
public class GroupLeaderOnlyException extends CommonException {

    public GroupLeaderOnlyException() {
        super(HttpStatus.FORBIDDEN, "GROUP_LEADER_ONLY", new MessageRef("error.GROUP_LEADER_ONLY"));
    }
}

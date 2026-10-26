package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 모임장은 나가거나 강퇴·역할 대상이 될 수 없다 — 넘기거나 해산해야 한다(409) — 코드 {@code GROUP_LEADER_CANNOT_LEAVE} */
public class GroupLeaderCannotLeaveException extends CommonException {

    public GroupLeaderCannotLeaveException() {
        super(HttpStatus.CONFLICT, "GROUP_LEADER_CANNOT_LEAVE", new MessageRef("error.GROUP_LEADER_CANNOT_LEAVE"));
    }
}

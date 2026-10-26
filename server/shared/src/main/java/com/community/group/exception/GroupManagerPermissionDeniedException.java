package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 매니저가 맡지 않은 일을 했다(403 — 3단계 매니저. 역할이 없거나 모임장 고정 일이면 {@code GROUP_LEADER_ONLY}) — 코드 {@code GROUP_MANAGER_PERMISSION_DENIED} */
public class GroupManagerPermissionDeniedException extends CommonException {

    public GroupManagerPermissionDeniedException() {
        super(HttpStatus.FORBIDDEN, "GROUP_MANAGER_PERMISSION_DENIED", new MessageRef("error.GROUP_MANAGER_PERMISSION_DENIED"));
    }
}

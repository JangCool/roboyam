package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 강퇴를 맡은 매니저가 모임장·매니저·자기 자신을 내보내려 했다(403 — 3단계 매니저) — 코드 {@code GROUP_MANAGER_PROTECTED} */
public class GroupManagerProtectedException extends CommonException {

    public GroupManagerProtectedException() {
        super(HttpStatus.FORBIDDEN, "GROUP_MANAGER_PROTECTED", new MessageRef("error.GROUP_MANAGER_PROTECTED"));
    }
}

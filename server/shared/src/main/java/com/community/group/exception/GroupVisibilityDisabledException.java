package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공개 범위 기능이 꺼져 있어 비공개→공개 변경·공개 요청·처리를 할 수 없다(403, api-groups.md 11-4·11-5) — 코드 {@code GROUP_VISIBILITY_DISABLED} */
public class GroupVisibilityDisabledException extends CommonException {

    public GroupVisibilityDisabledException() {
        super(HttpStatus.FORBIDDEN, "GROUP_VISIBILITY_DISABLED", new MessageRef("error.GROUP_VISIBILITY_DISABLED"));
    }
}

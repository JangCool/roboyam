package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 운영 조치로 비활성인 소모임에 쓰기를 했다(403) — 코드 {@code GROUP_INACTIVE} */
public class GroupInactiveException extends CommonException {

    public GroupInactiveException() {
        super(HttpStatus.FORBIDDEN, "GROUP_INACTIVE", new MessageRef("error.GROUP_INACTIVE"));
    }
}

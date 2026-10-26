package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 처리할 가입 신청이 없다(다른 사람이 먼저 처리했을 수 있다)(404) — 코드 {@code GROUP_APPLICATION_NOT_FOUND} */
public class GroupApplicationNotFoundException extends CommonException {

    public GroupApplicationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_APPLICATION_NOT_FOUND", new MessageRef("error.GROUP_APPLICATION_NOT_FOUND"));
    }
}

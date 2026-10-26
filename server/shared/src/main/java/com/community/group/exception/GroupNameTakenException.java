package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 같은 이름(공백·대소문자 무시)의 소모임이 열림·신청 중·비활성에 있다(409) — 코드 {@code GROUP_NAME_TAKEN} */
public class GroupNameTakenException extends CommonException {

    public GroupNameTakenException() {
        super(HttpStatus.CONFLICT, "GROUP_NAME_TAKEN", new MessageRef("error.GROUP_NAME_TAKEN"));
    }
}

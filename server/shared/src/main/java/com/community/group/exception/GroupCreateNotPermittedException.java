package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 회원 역할에 소모임 개설(group.create) 권한이 없다(403 — 로보얌 추가) — 코드 {@code GROUP_CREATE_NOT_PERMITTED} */
public class GroupCreateNotPermittedException extends CommonException {

    public GroupCreateNotPermittedException() {
        super(HttpStatus.FORBIDDEN, "GROUP_CREATE_NOT_PERMITTED", new MessageRef("error.GROUP_CREATE_NOT_PERMITTED"));
    }
}

package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 소모임이 없다 — 없는 ID · 해산 · 신청 중·반려(본인 신청은 내 소모임에서 본다) (404) — 코드 {@code GROUP_NOT_FOUND} */
public class GroupNotFoundException extends CommonException {

    public GroupNotFoundException() {
        super(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", new MessageRef("error.GROUP_NOT_FOUND"));
    }
}

package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 공개 소모임이 아니다(409) — AI 전용 소모임은 공개 소모임만 지정한다(ai-members-contract 정책 7). 코드 {@code GROUP_NOT_PUBLIC} */
public class GroupNotPublicException extends CommonException {

    public GroupNotPublicException() {
        super(HttpStatus.CONFLICT, "GROUP_NOT_PUBLIC", new MessageRef("error.GROUP_NOT_PUBLIC"));
    }
}

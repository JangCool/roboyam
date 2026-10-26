package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 내가 모임장인 소모임(신청 중·열림·비활성)이 1인 상한에 닿았다(409) — 문구에 상한 수, 오류 본문 최상위 {@code max} */
public class GroupLimitReachedException extends CommonException {

    public GroupLimitReachedException(int max) {
        super(HttpStatus.CONFLICT, "GROUP_LIMIT_REACHED", new MessageRef("error.GROUP_LIMIT_REACHED", String.valueOf(max)));
        extra("max", max);
    }
}

package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 앞으로 열려 있는(시작 전·진행 중) 일정 수가 운영 한도({@code eventMaxUpcoming})에 닿았다(409 — 4단계) — 문구에 한도 수, 오류 본문 최상위 {@code limit}
 * (화면 {@code ApiError.extras.limit}).
 */
public class GroupEventLimitReachedException extends CommonException {

    public GroupEventLimitReachedException(int limit) {
        super(HttpStatus.CONFLICT, "GROUP_EVENT_LIMIT_REACHED", new MessageRef("error.GROUP_EVENT_LIMIT_REACHED", String.valueOf(limit)));
        extra("limit", limit);
    }
}

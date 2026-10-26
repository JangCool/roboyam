package com.community.group.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 새로 임명하는데 지금 매니저 수가 소모임의 유효 상한(min(소모임 상한, 매니저 규칙의 최대 인원)) 이상이다(409 — 3단계) — 문구에 상한 수, 오류 본문 최상위 {@code limit}
 * (화면 {@code ApiError.extras.limit}). 맡은 일 바꾸기는 상한을 다시 보지 않는다.
 */
public class GroupManagerLimitReachedException extends CommonException {

    public GroupManagerLimitReachedException(int limit) {
        super(HttpStatus.CONFLICT, "GROUP_MANAGER_LIMIT_REACHED", new MessageRef("error.GROUP_MANAGER_LIMIT_REACHED", String.valueOf(limit)));
        extra("limit", limit);
    }
}

package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 이 글에서 입장을 정한 회원이 다른 입장으로 쓰려 한다(409) — 입장은 {@code PUT /api/posts/{id}/stance} 로만 바꾼다. 동시에 처음 쓴 두 요청 중 늦은 쪽도 이것을 받는다 */
public class StanceLockedException extends CommonException {

    public StanceLockedException() {
        super(HttpStatus.CONFLICT, "STANCE_LOCKED", new MessageRef("error.STANCE_LOCKED"));
    }
}

package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 의견 쓰기에 보낸 {@code stance} 가 내 지금 입장과 다르다(409) — 다른 창에서 입장을 바꿨을 때 옛 입장으로 쓰지 않게 막는다. 입장은 {@code PUT /api/posts/{id}/stance} 로만 바꾼다 */
public class StanceLockedException extends CommonException {

    public StanceLockedException() {
        super(HttpStatus.CONFLICT, "STANCE_LOCKED", new MessageRef("error.STANCE_LOCKED"));
    }
}

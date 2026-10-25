package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 입장 의견을 쓰려는데 아직 이 글에서 입장을 고르지 않았다(409) — 먼저 {@code PUT /api/posts/{id}/stance} 로 투표한다 */
public class StanceRequiredException extends CommonException {

    public StanceRequiredException() {
        super(HttpStatus.CONFLICT, "STANCE_REQUIRED", new MessageRef("error.STANCE_REQUIRED"));
    }
}

package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 의견 쓰기(의견에 단 답글 포함)·입장 고르기인데 이 글은 지금 토론 의견을 받지 않는다(409) — 하이라이트가 아니거나 「찬성·반대 의견 받기」가 꺼졌거나 노출이 끝났다 */
public class DebateNotOpenException extends CommonException {

    public DebateNotOpenException() {
        super(HttpStatus.CONFLICT, "DEBATE_NOT_OPEN", new MessageRef("error.DEBATE_NOT_OPEN"));
    }
}

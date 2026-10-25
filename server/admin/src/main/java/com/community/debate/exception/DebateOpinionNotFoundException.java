package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 대상이 그 글의 의견이 아니다(404) — 일반 댓글·답글·다른 글의 댓글·지운 댓글·형식이 틀린 ID */
public class DebateOpinionNotFoundException extends CommonException {

    public DebateOpinionNotFoundException() {
        super(HttpStatus.NOT_FOUND, "DEBATE_OPINION_NOT_FOUND", new MessageRef("error.DEBATE_OPINION_NOT_FOUND"));
    }
}

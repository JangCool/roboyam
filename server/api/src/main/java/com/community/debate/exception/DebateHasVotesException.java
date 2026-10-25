package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 글쓴이는 투표가 하나라도 있는 토론을 닫을 수 없다(409) — 설정(중립·바꾸기)은 바꿀 수 있다. 매니저·운영자는 닫을 수 있다 */
public class DebateHasVotesException extends CommonException {

    public DebateHasVotesException() {
        super(HttpStatus.CONFLICT, "DEBATE_HAS_VOTES", new MessageRef("error.DEBATE_HAS_VOTES"));
    }
}

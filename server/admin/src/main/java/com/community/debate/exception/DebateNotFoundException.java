package com.community.debate.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 그 글에 열린 토론이 없다(404) — {@code GET /api/admin/debates/{postId}} */
public class DebateNotFoundException extends CommonException {

    public DebateNotFoundException() {
        super(HttpStatus.NOT_FOUND, "DEBATE_NOT_FOUND", new MessageRef("error.DEBATE_NOT_FOUND"));
    }
}

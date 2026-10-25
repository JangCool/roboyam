package com.community.poll.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이 글에는 투표가 없다(404) — 공개({@code GET·투표·지우기})·관리자(투표 지우기)가 함께 쓴다 */
public class PollNotFoundException extends CommonException {

    public PollNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POLL_NOT_FOUND", new MessageRef("error.POLL_NOT_FOUND"));
    }
}

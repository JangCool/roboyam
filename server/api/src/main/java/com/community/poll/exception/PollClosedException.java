package com.community.poll.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 마감된 투표(409) */
public class PollClosedException extends CommonException {

    public PollClosedException() {
        super(HttpStatus.CONFLICT, "POLL_CLOSED", new MessageRef("error.POLL_CLOSED"));
    }
}

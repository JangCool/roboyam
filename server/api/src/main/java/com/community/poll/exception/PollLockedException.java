package com.community.poll.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 첫 표가 들어온 투표는 고치거나 뺄 수 없다(409) — 지금과 똑같은 내용을 다시 보내면 오류가 아니다(글만 고친다). 운영자는 표가 있어도 지울 수 있다 */
public class PollLockedException extends CommonException {

    public PollLockedException() {
        super(HttpStatus.CONFLICT, "POLL_LOCKED", new MessageRef("error.POLL_LOCKED"));
    }
}

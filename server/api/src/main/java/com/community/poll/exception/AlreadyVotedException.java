package com.community.poll.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 투표했고 「다시 투표 허용」이 꺼진 투표에 다른 선택을 보냈다(409) — 같은 선택은 200(멱등) */
public class AlreadyVotedException extends CommonException {

    public AlreadyVotedException() {
        super(HttpStatus.CONFLICT, "ALREADY_VOTED", new MessageRef("error.ALREADY_VOTED"));
    }
}

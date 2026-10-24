package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 자기 자신에게 메모(BAD_REQUEST {@code MEMO_SELF}) */
public class MemoSelfException extends CommonException {

    public MemoSelfException() {
        super(HttpStatus.BAD_REQUEST, "MEMO_SELF", new MessageRef("error.MEMO_SELF"));
    }
}

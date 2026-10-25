package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 이미 차단한 단어(대소문자 무시)(CONFLICT {@code BLOCKED_WORD_DUPLICATE}) */
public class BlockedWordDuplicateException extends CommonException {

    public BlockedWordDuplicateException() {
        super(HttpStatus.CONFLICT, "BLOCKED_WORD_DUPLICATE", new MessageRef("error.BLOCKED_WORD_DUPLICATE"));
    }
}

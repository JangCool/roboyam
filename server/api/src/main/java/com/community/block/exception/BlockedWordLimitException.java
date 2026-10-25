package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 차단 단어가 상한(409 {@code BLOCKED_WORD_LIMIT}) — 문구에 상한을 싣는다 */
public class BlockedWordLimitException extends CommonException {

    public BlockedWordLimitException(int limit) {
        super(HttpStatus.CONFLICT, "BLOCKED_WORD_LIMIT", new MessageRef("error.BLOCKED_WORD_LIMIT", String.valueOf(limit)));
    }
}

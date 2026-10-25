package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 자기 자신을 차단(BAD_REQUEST {@code BLOCK_SELF}) */
public class BlockSelfException extends CommonException {

    public BlockSelfException() {
        super(HttpStatus.BAD_REQUEST, "BLOCK_SELF", new MessageRef("error.BLOCK_SELF"));
    }
}

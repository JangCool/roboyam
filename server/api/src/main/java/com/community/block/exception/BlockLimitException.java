package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 차단한 회원이 상한(409 {@code BLOCK_LIMIT_REACHED}) — 문구에 상한을 싣는다 */
public class BlockLimitException extends CommonException {

    public BlockLimitException(int limit) {
        super(HttpStatus.CONFLICT, "BLOCK_LIMIT_REACHED", new MessageRef("error.BLOCK_LIMIT_REACHED", String.valueOf(limit)));
    }
}

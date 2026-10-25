package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 운영진(게시판 매니저·스텝을 맡은 회원)을 차단(FORBIDDEN {@code BLOCK_TARGET_PROTECTED}) */
public class BlockTargetProtectedException extends CommonException {

    public BlockTargetProtectedException() {
        super(HttpStatus.FORBIDDEN, "BLOCK_TARGET_PROTECTED", new MessageRef("error.BLOCK_TARGET_PROTECTED"));
    }
}

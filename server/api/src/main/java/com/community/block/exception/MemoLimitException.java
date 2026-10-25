package com.community.block.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 메모가 상한(처음 남길 때만 센다)(409 {@code MEMO_LIMIT_REACHED}) — 문구에 상한을 싣는다 */
public class MemoLimitException extends CommonException {

    public MemoLimitException(int limit) {
        super(HttpStatus.CONFLICT, "MEMO_LIMIT_REACHED", new MessageRef("error.MEMO_LIMIT_REACHED", String.valueOf(limit)));
    }
}

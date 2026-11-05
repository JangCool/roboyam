package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 한 사람의 심사 대기 팩이 상한(설정 {@code pending_limit}, 기본 3 — 계약 가정 9)에 닿았다. 새 판 심사도 센다 */
public class EmoticonPendingLimitException extends CommonException {

    public EmoticonPendingLimitException(int limit) {
        super(HttpStatus.CONFLICT, "EMOTICON_PENDING_LIMIT", new MessageRef("error.EMOTICON_PENDING_LIMIT", limit));
    }
}

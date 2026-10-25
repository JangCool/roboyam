package com.community.post.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 게시글 관리 — 게시판 정책 {@code pinnedMax} 를 넘는 고정(409 PIN_LIMIT). 한도가 0 이면 고정할 수 없다 */
public class PinLimitException extends CommonException {

    public PinLimitException(int max) {
        super(HttpStatus.CONFLICT, "PIN_LIMIT", new MessageRef("error.PIN_LIMIT", String.valueOf(max)));
    }
}

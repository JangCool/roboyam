package com.community.notice.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 전체 공지는 사이트 전체에서 동시에 {@code max} 개까지(409 NOTICE_GLOBAL_LIMIT, V174) — 게시판 고정 한도(PIN_LIMIT)와 따로 센다 */
public class NoticeGlobalLimitException extends CommonException {

    public NoticeGlobalLimitException(int max) {
        super(HttpStatus.CONFLICT, "NOTICE_GLOBAL_LIMIT", new MessageRef("error.NOTICE_GLOBAL_LIMIT", String.valueOf(max)));
    }
}

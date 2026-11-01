package com.community.hotdeal.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 「아직 진행 중」 횟수(keepMaxCount)를 다 썼다(409) — 화면은 「운영자 확인 중」 */
public class DealKeepLimitException extends CommonException {

    public DealKeepLimitException() {
        super(HttpStatus.CONFLICT, "DEAL_KEEP_LIMIT", new MessageRef("error.DEAL_KEEP_LIMIT"));
    }
}

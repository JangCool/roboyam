package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그 작가는 정산 정보(계좌)를 등록하지 않았다(운영자 원문 보기 — S2) */
public class EmoticonPayoutNotFoundException extends CommonException {

    public EmoticonPayoutNotFoundException() {
        super(HttpStatus.NOT_FOUND, "EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND", new MessageRef("error.EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND"));
    }
}

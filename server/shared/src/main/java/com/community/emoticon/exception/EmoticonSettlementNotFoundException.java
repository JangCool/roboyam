package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 그 정산 줄이 없다(형식이 틀린 ID · 판매·환불·넘어온 금액이 없는 작가·달 포함 — S4, 목업 코드 그대로) */
public class EmoticonSettlementNotFoundException extends CommonException {

    public static final String CODE = "EMOTICON_SETTLEMENT_NOT_FOUND";

    public EmoticonSettlementNotFoundException() {
        super(HttpStatus.NOT_FOUND, CODE, new MessageRef("error." + CODE));
    }
}

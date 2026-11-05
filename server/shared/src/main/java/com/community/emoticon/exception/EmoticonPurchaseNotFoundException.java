package com.community.emoticon.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 404 — 구매 기록이 없다. 회원 쪽은 {@code EMOTICON_PURCHASE_NOT_FOUND}(형식이 틀린 ID · 남의 구매 · 결제되지 않은 구매 포함), 관리자 판매 내역은 {@code EMOTICON_SALE_NOT_FOUND}
 * (두 앱 목업의 코드 그대로).
 */
public class EmoticonPurchaseNotFoundException extends CommonException {

    public static final String PURCHASE = "EMOTICON_PURCHASE_NOT_FOUND";
    public static final String SALE = "EMOTICON_SALE_NOT_FOUND";

    public EmoticonPurchaseNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, code, new MessageRef("error." + code));
    }
}

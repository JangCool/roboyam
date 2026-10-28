package com.community.commerce.sales;

import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.exception.CommonException;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 판매 스위치 오류(commerce.md 20절) — 켜기 조건이 덜 찼다(409 {@code COMMERCE_SALES_NOT_READY}, 추가 값 {@code missing} = 빠진 자동 조건·체크 항목 키) ·
 * 켜기는 최고 관리자만(403 {@code COMMERCE_SALES_ROOT_ONLY}).
 */
public class CommerceSalesException extends CommonException {

    public static final String NOT_READY = "COMMERCE_SALES_NOT_READY";
    public static final String ROOT_ONLY = "COMMERCE_SALES_ROOT_ONLY";

    private CommerceSalesException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    public static CommerceSalesException notReady(List<String> missing) {
        CommerceSalesException e = new CommerceSalesException(HttpStatus.CONFLICT, NOT_READY);
        e.extra("missing", missing);
        return e;
    }

    public static CommerceSalesException rootOnly() {
        return new CommerceSalesException(HttpStatus.FORBIDDEN, ROOT_ONLY);
    }
}

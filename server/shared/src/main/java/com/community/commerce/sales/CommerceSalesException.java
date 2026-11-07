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
    /** 판매가 켜져 있어 운영 주체를 개인 단계로 바꾸거나 통신판매업 신고번호를 비울 수 없다(409 — 먼저 판매를 끈다, terms-operator-contract) */
    public static final String SALES_OPEN = "COMMERCE_SALES_OPEN";
    /** 로보티콘 유료 판매가 켜져 있어 운영 주체를 개인 단계로 바꾸거나 통신판매업 신고번호를 비울 수 없다(409 — 먼저 로보티콘 설정에서 유료 판매를 끈다) */
    public static final String EMOTICON_PAID_SALES_OPEN = "EMOTICON_PAID_SALES_OPEN";

    private CommerceSalesException(HttpStatus status, String code) {
        super(status, code, new MessageRef("error." + code));
    }

    public static CommerceSalesException notReady(List<String> missing) {
        CommerceSalesException e = new CommerceSalesException(HttpStatus.CONFLICT, NOT_READY);
        e.extra("missing", missing);
        return e;
    }

    public static CommerceSalesException salesOpen() {
        return new CommerceSalesException(HttpStatus.CONFLICT, SALES_OPEN);
    }

    public static CommerceSalesException emoticonPaidSalesOpen() {
        return new CommerceSalesException(HttpStatus.CONFLICT, EMOTICON_PAID_SALES_OPEN);
    }

    public static CommerceSalesException rootOnly() {
        return new CommerceSalesException(HttpStatus.FORBIDDEN, ROOT_ONLY);
    }
}

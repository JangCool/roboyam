package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 지금은 팔지 않는 품목(판매 예정·판매 끝·사용 안 함) */
public class PointShopNotOnSaleException extends CommonException {

    public PointShopNotOnSaleException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_NOT_ON_SALE", new MessageRef("error.POINT_SHOP_NOT_ON_SALE"));
    }
}

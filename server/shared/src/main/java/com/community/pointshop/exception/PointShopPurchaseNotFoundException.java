package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 구매(관리자 예외 환불) */
public class PointShopPurchaseNotFoundException extends CommonException {

    public PointShopPurchaseNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POINT_SHOP_PURCHASE_NOT_FOUND", new MessageRef("error.POINT_SHOP_PURCHASE_NOT_FOUND"));
    }
}

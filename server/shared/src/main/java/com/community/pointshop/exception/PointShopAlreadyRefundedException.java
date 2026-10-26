package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 환불한 구매 */
public class PointShopAlreadyRefundedException extends CommonException {

    public PointShopAlreadyRefundedException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_ALREADY_REFUNDED", new MessageRef("error.POINT_SHOP_ALREADY_REFUNDED"));
    }
}

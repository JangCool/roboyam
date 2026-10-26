package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 품절(재고 0 — 마지막 하나를 다른 회원이 먼저 샀을 때도) */
public class PointShopSoldOutException extends CommonException {

    public PointShopSoldOutException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_SOLD_OUT", new MessageRef("error.POINT_SHOP_SOLD_OUT"));
    }
}

package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 가진 영구 아이템(환불하지 않은 것) */
public class PointShopAlreadyOwnedException extends CommonException {

    public PointShopAlreadyOwnedException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_ALREADY_OWNED", new MessageRef("error.POINT_SHOP_ALREADY_OWNED"));
    }
}

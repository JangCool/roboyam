package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 포인트 상점 품목 */
public class PointShopItemNotFoundException extends CommonException {

    public PointShopItemNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POINT_SHOP_ITEM_NOT_FOUND", new MessageRef("error.POINT_SHOP_ITEM_NOT_FOUND"));
    }
}

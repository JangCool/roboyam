package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 404 — 없는 환불 신청 */
public class PointShopRefundRequestNotFoundException extends CommonException {

    public PointShopRefundRequestNotFoundException() {
        super(HttpStatus.NOT_FOUND, "POINT_SHOP_REFUND_REQUEST_NOT_FOUND", new MessageRef("error.POINT_SHOP_REFUND_REQUEST_NOT_FOUND"));
    }
}

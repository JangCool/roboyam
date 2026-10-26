package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 이미 수락·거절된 환불 신청 */
public class PointShopRefundRequestNotPendingException extends CommonException {

    public PointShopRefundRequestNotPendingException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_REFUND_REQUEST_NOT_PENDING", new MessageRef("error.POINT_SHOP_REFUND_REQUEST_NOT_PENDING"));
    }
}

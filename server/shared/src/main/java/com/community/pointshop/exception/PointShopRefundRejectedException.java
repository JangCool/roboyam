package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 환불 신청이 거절된 구매는 정지 중 다시 신청할 수 없다(정지가 풀리면 기한 안 셀프 환불은 된다) */
public class PointShopRefundRejectedException extends CommonException {

    public PointShopRefundRejectedException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_REFUND_REJECTED", new MessageRef("error.POINT_SHOP_REFUND_REJECTED"));
    }
}

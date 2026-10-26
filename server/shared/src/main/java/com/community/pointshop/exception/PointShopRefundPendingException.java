package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/** 409 — 환불 신청이 운영자 확인을 기다리는 중(장착·아바타 고르기·셀프 환불·다시 신청·예외 환불·변경권 사용 불가 — 써 버리면 신청 조건이 깨진다) */
public class PointShopRefundPendingException extends CommonException {

    public PointShopRefundPendingException() {
        super(HttpStatus.CONFLICT, "POINT_SHOP_REFUND_PENDING", new MessageRef("error.POINT_SHOP_REFUND_PENDING"));
    }
}

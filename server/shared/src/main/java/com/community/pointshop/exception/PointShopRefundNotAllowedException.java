package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 — 셀프 환불 조건 밖(commerce.md 7-3 D6): 이미 썼거나(장착·사용·프로필로 고름 — {@code first_used_at}) 환불 기한(지금 설정의 {@code refund_days})이 지났다.
 * 오류 본문 최상위 {@code blockedBy} = {@code USED} | {@code EXPIRED}(화면은 목록을 다시 읽어 이유를 보인다).
 */
public class PointShopRefundNotAllowedException extends CommonException {

    public PointShopRefundNotAllowedException(String blockedBy) {
        super(HttpStatus.CONFLICT, "POINT_SHOP_REFUND_NOT_ALLOWED", new MessageRef("error.POINT_SHOP_REFUND_NOT_ALLOWED"));
        extra("blockedBy", blockedBy);
    }
}

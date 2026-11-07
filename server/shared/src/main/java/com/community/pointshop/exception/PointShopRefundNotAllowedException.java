package com.community.pointshop.exception;

import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import org.springframework.http.HttpStatus;

/**
 * 409 — 셀프 환불 조건 밖(commerce.md 7-3 D6): 이미 썼거나(장착·사용·프로필로 고름 — {@code first_used_at}) 환불 기한(지금 설정의 {@code refund_days})이 지났다.
 * 오류 본문 최상위 {@code blockedBy} = {@code USED} | {@code EXPIRED} | {@code RESERVED}(올리기 변경권이 대기 신청에 예약됨 — commerce-motion-avatar-contract 규칙 0)
 * | {@code GRANTED}(보상 이벤트로 받은 줄 — event-admin-contract 「보상 줄」, 구매가 아니라 환불 대상이 아니다) (화면은 목록을 다시 읽어 이유를 보인다).
 */
public class PointShopRefundNotAllowedException extends CommonException {

    public PointShopRefundNotAllowedException(String blockedBy) {
        super(HttpStatus.CONFLICT, "POINT_SHOP_REFUND_NOT_ALLOWED", new MessageRef(messageKey(blockedBy)));
        extra("blockedBy", blockedBy);
    }

    private static String messageKey(String blockedBy) {
        if (com.community.pointshop.service.PointShopRules.BLOCKED_RESERVED.equals(blockedBy)) {
            return "error.POINT_SHOP_REFUND_NOT_ALLOWED.RESERVED";
        }
        if (com.community.pointshop.service.PointShopRules.BLOCKED_GRANTED.equals(blockedBy)) {
            return "error.POINT_SHOP_REFUND_NOT_ALLOWED.GRANTED";
        }
        return "error.POINT_SHOP_REFUND_NOT_ALLOWED";
    }
}

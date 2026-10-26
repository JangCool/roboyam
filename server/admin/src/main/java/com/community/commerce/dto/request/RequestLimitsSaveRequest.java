package com.community.commerce.dto.request;

import java.util.Map;

/**
 * 요청 제한 저장 — {@code limits} 에 네 종류(GROUPBUY_JOIN · POINT_CHARGE · POINT_SHOP_PURCHASE · REFUND_REQUEST)를 모두 보낸다(정수 0~100, 0 = 제한 없음).
 * 값은 숫자가 아닐 수 있어 {@code Object} 로 받고 서비스가 칸마다 검사한다(틀린 칸은 {@code fields.limits.<코드>}).
 */
public record RequestLimitsSaveRequest(Map<String, Object> limits) {
}

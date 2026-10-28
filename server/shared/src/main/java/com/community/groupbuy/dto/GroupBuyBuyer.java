package com.community.groupbuy.dto;

/**
 * 공구 주문하는 회원 — {@code purchasePermitted} 는 회원 권한 {@code commerce.purchase} 를 가졌는가(세션의 권한으로 컨트롤러가 정한다, 포인트 상점의 {@code PointShopBuyer} 와 같은 모양).
 */
public record GroupBuyBuyer(long userId, boolean purchasePermitted) {
}

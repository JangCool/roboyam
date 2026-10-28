package com.community.groupbuy.dto;

import java.util.List;

/**
 * 공구 주문(참여) 요청 — 옵션 여러 줄 × 수량, 배송지, 동의 3종(구매 조건·환불 안내·개인정보 — 기본 체크 없음, 결정 59),
 * 화면이 본 금액 {@code expectedAmount}(비교용 — 다르면 409 {@code GROUP_BUY_AMOUNT_CHANGED}, 청구액은 늘 서버 계산 — 결정 72).
 */
public record JoinRequest(long groupBuyId, List<OrderLine> lines, ShippingAddress address, boolean agreePurchaseTerms, boolean agreeRefundPolicy,
                          boolean agreePrivacy, Long expectedAmount) {
}

package com.community.pointshop.dto.response;

import java.util.List;

/**
 * {@code GET /api/point-shop/items?kind=} — 판매 끝난 품목(끝 지남·사용 안 함)은 오지 않는다(상세로는 열린다). 거르기가 없으면 아바타(306종 — {@code GET /api/avatars})는 섞지 않는다.
 *
 * @param refundDays 셀프 환불 기한 일수(지금 설정)
 * @param balance    지금 내 포인트(충전을 열기 전이라 합계 하나 — D13)
 */
public record PointShopItemListView(int refundDays, long balance, List<PointShopItemView> items) {
}

package com.community.groupbuy.dto;

import java.util.List;

/**
 * 견적(화면 표시용 — 주문 때 같은 계산을 다시 한다, commerce.md 3절). 배송비·무료배송 기준은 주문 전체 상품 금액으로(결정 23 — 추가 구성상품 포함, 결정 88).
 *
 * @param freeShippingOver 무료배송 기준(없으면 null)
 */
public record Quote(long groupBuyId, List<Line> items, long itemsAmount, long shippingFee, long amount, Long freeShippingOver) {

    /** 견적 줄 — 단가: MAIN = 공구가 + 추가금, ADDON = 자기 가격 */
    public record Line(long skuId, String kind, String name, int qty, long unitPrice, long itemAmount) {
    }
}

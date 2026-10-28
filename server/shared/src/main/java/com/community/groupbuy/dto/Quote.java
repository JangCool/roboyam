package com.community.groupbuy.dto;

import java.util.List;

/**
 * 견적(화면 표시용 — 주문 때 같은 계산을 다시 한다, commerce.md 3절). 배송비·무료배송 기준은 주문 전체 상품 금액으로(결정 23).
 *
 * @param freeShippingOver 무료배송 기준(없으면 null)
 */
public record Quote(long groupBuyId, List<Line> lines, long itemsAmount, long shippingFee, long amount, Long freeShippingOver) {

    /** 견적 줄 — 단가 = 공구가 + 옵션 추가금 */
    public record Line(Long optionId, String optionName, int qty, long unitPrice, long itemAmount) {
    }
}

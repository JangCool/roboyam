package com.community.groupbuy.service;

/**
 * 공구 금액 계산 한 곳 — 견적·주문·본인 취소가 같은 식을 쓴다(commerce.md 3절 「돈·수량·상태는 서버가 정한다」).
 * 단가 = MAIN 단품 = 공구가 + 추가금, 추가 구성상품 = 자기 가격, 배송비는 주문 전체 상품 금액으로(결정 23): 무료배송 기준이 있고 그 이상이면 0, 아니면 공구가 고정한 배송비.
 */
public final class GroupBuyPricing {

    private GroupBuyPricing() {
    }

    public static long unitPrice(long groupPrice, long extraPrice) {
        return Math.addExact(groupPrice, extraPrice);
    }

    /** 상품 금액에 붙는 배송비 */
    public static long shippingFee(long itemsAmount, long baseFee, Long freeShippingOver) {
        if (itemsAmount <= 0) {
            return 0;
        }
        return freeShippingOver != null && itemsAmount >= freeShippingOver ? 0 : baseFee;
    }

    /**
     * 발송 전 일부 줄 취소의 재부과 배송비(결정 42) — 남은 상품 기준 배송비에서 이미 낸 배송비·이미 재부과한 배송비를 뺀 차액(음수면 0). 남은 상품이 없으면(전체 취소) 0.
     */
    public static long reshippingFee(long remainingItemsAmount, long baseFee, Long freeShippingOver, long paidShippingFee, long alreadyCharged) {
        if (remainingItemsAmount <= 0) {
            return 0;
        }
        long needed = shippingFee(remainingItemsAmount, baseFee, freeShippingOver);
        return Math.max(0, needed - paidShippingFee - alreadyCharged);
    }
}

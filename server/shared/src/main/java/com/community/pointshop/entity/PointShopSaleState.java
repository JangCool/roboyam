package com.community.pointshop.entity;

import java.time.Instant;

/**
 * 판매 상태(서버 계산 — 저장하지 않는다) — 판매 중 · 판매 예정(시작 전) · 판매 끝(끝 지남 또는 사용 안 함) · 품절(재고 0). user-web {@code PointShopSaleState} 와 같은 값.
 */
public enum PointShopSaleState {
    ON_SALE,
    UPCOMING,
    ENDED,
    SOLD_OUT;

    /** 판단 차례: 사용 안 함 → 판매 끝 · 시작 전 → 판매 예정 · 끝 지남 → 판매 끝 · 재고 0 → 품절 · 그 밖 판매 중 */
    public static PointShopSaleState of(boolean active, Instant saleStartAt, Instant saleEndAt, Integer stock, Instant now) {
        if (!active) {
            return ENDED;
        }
        if (saleStartAt != null && saleStartAt.isAfter(now)) {
            return UPCOMING;
        }
        if (saleEndAt != null && !saleEndAt.isAfter(now)) {
            return ENDED;
        }
        if (stock != null && stock <= 0) {
            return SOLD_OUT;
        }
        return ON_SALE;
    }
}

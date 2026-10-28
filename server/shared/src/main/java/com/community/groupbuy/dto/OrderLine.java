package com.community.groupbuy.dto;

/**
 * 주문 줄 하나 — 단품과 수량(결정 23·88). 금액은 받지 않는다(서버가 정한다, commerce.md 3절). 요청 본문의 {@code items[{skuId, qty}]}.
 */
public record OrderLine(long skuId, int qty) {
}

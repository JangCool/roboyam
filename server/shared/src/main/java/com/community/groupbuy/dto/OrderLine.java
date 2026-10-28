package com.community.groupbuy.dto;

/**
 * 주문 줄 하나 — 옵션(옵션 없는 공구는 null)과 수량(결정 23). 금액은 받지 않는다(서버가 정한다, commerce.md 3절).
 */
public record OrderLine(Long optionId, int qty) {
}

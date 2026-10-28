package com.community.groupbuy.dto;

/**
 * 추가 입고·줄이기 결과(결정 17·80).
 *
 * @param skuMaxQty 단품을 지정했으면 그 단품의 새 최대
 * @param closed       줄인 결과 다 팔려 그 자리에서 CLOSING 이 됐는가
 */
public record StockChange(long groupBuyId, int maxQty, int addedQty, Integer skuMaxQty, boolean closed) {
}

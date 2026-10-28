package com.community.groupbuy.dto;

import java.util.List;

/**
 * 상세 화면의 옵션 축·값과 단품별 남은 수량·품절 구분(결정 17·84·88) — 「다 팔림」(확정 = 최대)과 「잠시 구매 불가」(확정 + 선점 = 최대 — 다른 분이 결제 중)를 나눈다.
 * MAIN 단품 남은 = min(단품 남은, 공구 남은)(단품 한도가 없으면 공구 값), ADDON 단품 남은 = 자기 한도 기준(한도가 없으면 null = 제한 없음).
 */
public record Availability(long groupBuyId, int remaining, boolean soldOut, boolean temporarilyUnavailable, int addedQty, List<Axis> axes, List<Sku> skus) {

    /** 옵션 축(차례대로) */
    public record Axis(long axisId, String name, List<Value> values) {
    }

    /** 축 값 */
    public record Value(long valueId, String value) {
    }

    /**
     * 단품 — {@code kind} MAIN/ADDON, {@code valueIds} 축 값 조합(MAIN, 축 차례), {@code remaining} null = 제한 없음(ADDON 한도 없음), {@code onSale} false 면 고를 수 없다(회색).
     */
    public record Sku(long skuId, String kind, String name, List<Long> valueIds, long unitPrice, Integer remaining, boolean soldOut, boolean temporarilyUnavailable,
                      boolean onSale) {
    }
}

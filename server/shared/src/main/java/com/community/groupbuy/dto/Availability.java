package com.community.groupbuy.dto;

import java.util.List;

/**
 * 공구·옵션별 남은 수량과 품절 구분(결정 17·84) — 「다 팔림」(확정 = 최대)과 「잠시 구매 불가」(확정 + 선점 = 최대 — 다른 분이 결제 중)를 나눈다.
 * 옵션 남은 = min(옵션 남은, 공구 남은)(옵션 한도가 없으면 공구 값).
 */
public record Availability(long groupBuyId, int remaining, boolean soldOut, boolean temporarilyUnavailable, int addedQty, List<OptionAvailability> options) {

    public record OptionAvailability(long optionId, String name, int remaining, boolean soldOut, boolean temporarilyUnavailable) {
    }
}

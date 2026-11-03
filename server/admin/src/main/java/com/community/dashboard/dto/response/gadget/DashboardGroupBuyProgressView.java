package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code GROUPBUY_PROGRESS} — 모집 중(RECRUITING) 공구, 마감 임박 차례(최대 limit).
 *
 * @param items {@code joined} = 판매 수량({@code current_qty} — 결제 확정된 주문 줄의 판매 수량 합, 결제 중 선점은 넣지 않는다) · {@code goal} = 성사형은 최소 수량({@code min_qty}),
 *              선착순({@code min_qty = 0})은 최대 수량({@code max_qty}) · {@code endsAt} = 지금 마감 시각(연장 중이면 연장 끝 {@code extended_until}, 아니면 {@code end_at})
 */
public record DashboardGroupBuyProgressView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String title, int joined, int goal, Instant endsAt) {
    }
}

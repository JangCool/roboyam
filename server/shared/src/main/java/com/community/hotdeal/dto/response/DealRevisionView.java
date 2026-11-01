package com.community.hotdeal.dto.response;

import java.time.Instant;

/** 딜 칸 수정 요약(D3) — 「가격 수정 N회」(딜가·정상가, 배송비 제외) · 「링크 바뀜」(호스트가 바뀐 적) · 마지막으로 딜 칸을 고친 시각 */
public record DealRevisionView(int priceEdits, boolean linkChanged, Instant lastEditedAt) {
}
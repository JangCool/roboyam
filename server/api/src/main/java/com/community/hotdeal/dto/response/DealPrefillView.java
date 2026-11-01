package com.community.hotdeal.dto.response;

/** {@code GET /api/deals/prefill?from=}(user-web {@code DealPrefill}) — 끝난 딜에서 새 딜 올리기. 가격·마감은 싣지 않는다. 이미지는 OG 사본일 때만(올린 이미지는 그 글의 것이라 다시 못 쓴다) */
public record DealPrefillView(String fromPostId, String title, String storeName, String productUrl, DealImageView image) {
}
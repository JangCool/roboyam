package com.community.hotdeal.dto.request;

/** 종료 요청({@code POST /api/posts/{id}/deal/end-requests}) — 사유 {@code SOLD_OUT}·{@code PRICE_CHANGED}·{@code LINK_DEAD}(자유 글 없음) */
public record DealEndRequestInput(String reason) {
}

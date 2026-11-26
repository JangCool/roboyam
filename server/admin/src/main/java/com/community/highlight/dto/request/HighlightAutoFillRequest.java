package com.community.highlight.dto.request;

/**
 * {@code PUT /api/admin/highlights/places/{place}/auto-fill} — 게시판 곳의 「모자라면 추천 많은 글로 자동 채우기」(board-recommend-contract).
 *
 * @param autoFill 켬·끔(필수 — 없으면 {@code 400 fields.autoFill})
 */
public record HighlightAutoFillRequest(Boolean autoFill) {
}

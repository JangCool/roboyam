package com.community.highlight.dto.response;

import java.util.List;

/**
 * {@code GET /api/boards/{slug}/recommended} — 게시판 목록 위 추천 글 카드(최대 3장, 고른 글 → 자동 채움 차례). 비면 화면이 줄을 그리지 않는다.
 */
public record BoardRecommendView(List<BoardRecommendItemView> items) {

    public static final BoardRecommendView EMPTY = new BoardRecommendView(List.of());
}

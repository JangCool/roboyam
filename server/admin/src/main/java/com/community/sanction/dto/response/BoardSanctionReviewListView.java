package com.community.sanction.dto.response;

import java.util.List;

/** 반복 검토 목록 — 열린 건은 오래된 것이 위, 닫힌 건은 최근 닫은 것이 위(admin-web {@code BoardSanctionReviewListView}) */
public record BoardSanctionReviewListView(BoardSanctionReviewCountsView counts, long total, int page, int size, List<BoardSanctionReviewItemView> items) {
}

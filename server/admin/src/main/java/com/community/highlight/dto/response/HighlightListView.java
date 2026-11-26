package com.community.highlight.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/highlights?place=} — 한 곳의 목록.
 *
 * @param place        놓일 곳 키(보내지 않았으면 home)
 * @param visibleCount 상태가 SHOWING 인 수
 * @param total        전체 수
 * @param board        게시판 곳이면 그 게시판(이름·자동 채우기 스위치 — board-recommend-contract), 그 밖은 null
 */
public record HighlightListView(String place, int visibleCount, int total, List<HighlightItemView> items, HighlightBoardPlaceView board) {
}

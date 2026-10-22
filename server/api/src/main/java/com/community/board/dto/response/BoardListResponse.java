package com.community.board.dto.response;

import java.util.List;

/** {@code GET /api/boards}·{@code GET /api/boards/writable} — 정렬 순서대로 */
public record BoardListResponse(List<BoardView> boards) {
}

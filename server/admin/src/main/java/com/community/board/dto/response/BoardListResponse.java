package com.community.board.dto.response;

import java.util.List;

/** {@code GET /api/admin/boards} — 순서대로, 꺼진 것 포함 */
public record BoardListResponse(List<BoardListItemView> boards) {
}

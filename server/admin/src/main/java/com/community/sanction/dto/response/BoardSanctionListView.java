package com.community.sanction.dto.response;

import java.util.List;

/** {@code GET /api/admin/board-sanctions} — {@code counts} 거르기와 무관한 전체 건수, {@code boards} 게시판 거르기 고르개(제재가 한 건이라도 있는 게시판, 이름 차례) */
public record BoardSanctionListView(SanctionCountsView counts, long total, int page, int size, List<BoardSanctionLineView> items, List<BoardSanctionBoardRefView> boards) {
}

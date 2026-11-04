package com.community.boardstaff.dto.response;

import java.util.List;

/** {@code GET /api/board-staff/boards/{boardId}/sanctions}(user-web {@code BoardStaffSanctionPage}) — 매니저 = 그 게시판 전체, 스텝 = 내가 준 것만. {@code page} 1부터 */
public record BoardStaffSanctionPageView(boolean manager, List<BoardStaffSanctionRowView> items, int page, int totalPages, long totalCount) {
}

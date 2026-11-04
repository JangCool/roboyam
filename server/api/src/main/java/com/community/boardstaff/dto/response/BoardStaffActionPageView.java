package com.community.boardstaff.dto.response;

import java.util.List;

/** {@code GET /api/board-staff/boards/{boardId}/actions}(user-web {@code BoardStaffActionPage}) — 매니저 = 그 게시판 전체, 스텝 = 본인 것만. {@code page} 1부터 */
public record BoardStaffActionPageView(boolean manager, List<BoardStaffActionRowView> items, int page, int totalPages, long totalCount) {
}

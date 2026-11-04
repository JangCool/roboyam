package com.community.sanction.dto.response;

import java.util.List;

/** {@code GET /api/admin/board-staff-actions}(admin-web {@code BoardStaffActionListView}) — {@code page} 0부터, {@code boards} 는 게시판 거르기 고르개 */
public record AdminBoardStaffActionListView(long total, int page, int size, List<BoardSanctionBoardRefView> boards, List<AdminBoardStaffActionView> items) {
}

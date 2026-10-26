package com.community.group.dto.response;

import java.time.Instant;
import java.util.List;

/** {@code GET /api/admin/groups/requests} — 승인 대기 개설 신청(오래된 신청이 위). admin-web {@code GroupOpenRequestList} */
public record GroupOpenRequestListView(List<Item> items) {

    /**
     * admin-web {@code GroupOpenRequest}.
     *
     * @param stockPurposeAgreed 투자 고지 게시판에서 「공부·산업 해설 목적만」 추가 동의를 했다
     * @param visibility         신청한 공개 범위(기능 켜짐과 상관없이 실제 값)
     */
    public record Item(String id, String name, String description, GroupCategoryRefView category, GroupBoardRefView board, String joinType, int capacity,
                       boolean offline, GroupPersonView requester, Instant requestedAt, boolean stockPurposeAgreed, String visibility) {
    }
}

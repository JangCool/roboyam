package com.community.group.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/admin/groups} — admin-web {@code GroupListPage}. 최신순(개설일) 고정, {@code page} 는 0부터.
 *
 * @param total           지금 조건에 맞는 전체 개수
 * @param counts          칩 숫자 — 검색어만 적용(상태·성질·공개 범위 필터는 적용하지 않는다)
 * @param pendingRequests 승인 대기 개설 신청 수(조건과 무관)
 */
public record GroupAdminListView(List<Row> items, int page, int size, boolean hasNext, long total, Counts counts, long pendingRequests) {

    /**
     * admin-web {@code GroupRow}.
     *
     * @param status        ACTIVE · INACTIVE
     * @param recruitState  OPEN · FULL · CLOSED
     * @param stockNotice   투자 고지 게시판에서 추가 동의를 받은 소모임
     * @param visibility    실제 공개 범위(PUBLIC · PRIVATE)
     * @param deactivatedAt 지금 비활성일 때만
     */
    public record Row(String id, String name, GroupCategoryRefView category, GroupBoardRefView board, GroupPersonView leader, int memberCount, int capacity,
                      String status, String recruitState, String joinType, boolean offline, boolean stockNotice, String visibility, Instant openedAt,
                      Instant lastActivityAt, Instant deactivatedAt) {
    }

    public record Counts(long all, long active, long inactive, long closed, long offline) {
    }
}

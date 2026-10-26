package com.community.group.dto.response;

import java.util.List;

/** {@code GET /api/groups} — 쪽 번호(1부터)의 「더보기」. {@code total} 은 필터·검색에 맞는 전체 개수 */
public record GroupListPageView(List<GroupRowView> items, int page, boolean hasNext, long total) {
}

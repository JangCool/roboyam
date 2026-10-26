package com.community.group.dto.response;

import java.util.List;

/** {@code GET /api/groups/{id}/events} — 쪽 번호 1부터. {@code total} 은 그 범위(upcoming · past)의 전체 수 */
public record GroupEventPageView(List<GroupEventRowView> items, int page, boolean hasNext, long total) {
}

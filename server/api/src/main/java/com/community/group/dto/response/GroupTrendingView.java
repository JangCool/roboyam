package com.community.group.dto.response;

import java.util.List;

/** {@code GET /api/groups/trending} — 지금 뜨는 소모임(목록 줄과 같은 모양, 최대 관리자 설정 trendingSize 개). 후보가 없으면 빈 목록 */
public record GroupTrendingView(List<GroupRowView> items) {
}

package com.community.group.dto.response;

import java.util.List;

/**
 * {@code GET /api/groups/{id}/posts} — 글 탭 한 쪽(최신이 위, 커서로 더보기).
 *
 * @param nextCursor   다음 쪽 커서(마지막 줄의 글 ID) — 없으면 null
 * @param locked       내가 멤버가 아니라 비공개 글이 잠겨 있다
 * @param privateCount 잠긴 글 수(멤버는 0) — 「멤버에게만 보이는 글 N개」
 */
public record GroupPostPageView(List<GroupPostRowView> items, boolean hasNext, String nextCursor, boolean locked, long privateCount) {
}

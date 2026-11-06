package com.community.group.dto.response;

import java.util.List;

/**
 * {@code GET /api/groups/{id}/posts} — 글 탭 한 쪽. 커서로 더보기(최신이 위) 또는 쪽 번호({@code page} 를 보냈을 때 — 게시판 목록처럼, 2026-11-06 사용자).
 *
 * @param nextCursor   다음 쪽 커서(마지막 줄의 글 ID) — 없으면 null(쪽 번호로 받으면 늘 null)
 * @param locked       내가 멤버가 아니라 비공개 글이 잠겨 있다
 * @param privateCount 잠긴 글 수(멤버는 0) — 「멤버에게만 보이는 글 N개」
 * @param page         지금 쪽(1부터) — 커서로 받으면 null
 * @param totalPages   전체 쪽 수 — 커서로 받으면 null
 * @param totalCount   조건(말머리·검색)에 맞는 글 수 — 커서로 받으면 null
 */
public record GroupPostPageView(List<GroupPostRowView> items, boolean hasNext, String nextCursor, boolean locked, long privateCount, Integer page, Integer totalPages,
                                Long totalCount) {

    /** 커서로 받은 쪽 */
    public GroupPostPageView(List<GroupPostRowView> items, boolean hasNext, String nextCursor, boolean locked, long privateCount) {
        this(items, hasNext, nextCursor, locked, privateCount, null, null, null);
    }
}

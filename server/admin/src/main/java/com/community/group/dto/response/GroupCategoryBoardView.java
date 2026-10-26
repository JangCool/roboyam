package com.community.group.dto.response;

/**
 * 분류에 할당한 게시판 한 줄(admin-web {@code GroupCategoryBoard}) — 꺼진 게시판도 {@code active: false} 로 싣는다.
 *
 * @param groupCount 이 게시판을 쓰는 소모임 수(열림·비활성·대기) — 소모임 서버 단계 전까지 늘 0
 */
public record GroupCategoryBoardView(String boardId, String slug, String name, boolean active, boolean investmentNotice, long groupCount) {
}

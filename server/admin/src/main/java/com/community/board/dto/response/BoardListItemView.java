package com.community.board.dto.response;

/**
 * 게시판 목록의 한 줄(admin-web {@code BoardListItem}).
 *
 * @param sortOrder  1부터 세는 자리
 * @param postCount  삭제되지 않은 글 수(숨김 포함, 임시저장 제외)
 * @param diffCount  기본값과 다르게 정한 정책 칸 수 + 잠금이면 1
 * @param writeMin   유효 글쓰기 최소 자격(게시판 값 ?? 기본값)
 * @param commentMin 유효 댓글 최소 자격
 * @param groupCategoryId   할당된 소모임 분류(소모임 게시판만) — 분류에 안 들었거나 일반 게시판이면 null
 * @param groupCategoryName 그 분류 이름 — 없으면 null
 */
public record BoardListItemView(String id, String slug, String name, String nameEn, String description, String kind, String layout, String color,
                                boolean active, int sortOrder, long postCount, int managerCount, int staffCount, int diffCount, String writeMin,
                                String commentMin, String groupCategoryId, String groupCategoryName) {
}

package com.community.board.dto.response;

/**
 * 게시판 목록의 한 줄(admin-web {@code BoardListItem}).
 *
 * @param sortOrder  1부터 세는 자리
 * @param postCount  삭제되지 않은 글 수(숨김 포함, 임시저장 제외)
 * @param diffCount  기본값과 다르게 정한 정책 칸 수 + 잠금이면 1
 * @param writeMin   유효 글쓰기 최소 자격(게시판 값 ?? 기본값)
 * @param commentMin 유효 댓글 최소 자격
 */
public record BoardListItemView(String id, String slug, String name, String nameEn, String description, String kind, String layout, String color,
                                boolean active, int sortOrder, long postCount, int managerCount, int staffCount, int diffCount, String writeMin,
                                String commentMin) {
}

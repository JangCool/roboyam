package com.community.highlight.dto.response;

/**
 * 게시판 곳 하나(board-recommend-contract) — {@code GET /api/admin/highlights/boards} 의 줄, {@code GET /api/admin/highlights?place=board-…} 의 {@code board},
 * {@code PUT /api/admin/highlights/places/{place}/auto-fill} 의 응답.
 *
 * @param place    곳 키 {@code board-{boardId}}
 * @param boardId  게시판 ID
 * @param slug     게시판 주소
 * @param name     게시판 이름(요청 언어 — 영어면 영문 이름, 없으면 이름)
 * @param active   게시판이 켜져 있는가(꺼진 게시판은 공개에 카드가 없다)
 * @param autoFill 고른 글이 모자라면 그 게시판의 추천 많은 글로 채우는가(기본 켬)
 * @param total    그 곳의 하이라이트 수(노출 여부와 상관없이)
 * @param shown    공개에 보이는 최대 카드 수(위에서부터)
 */
public record HighlightBoardPlaceView(String place, String boardId, String slug, String name, boolean active, boolean autoFill, long total, int shown) {
}

package com.community.site.dto.response;

/**
 * 공개 게시판 표시 기본값 — {@code /api/site/info} 의 {@code boardDisplay}(user-web {@code BoardDisplaySettings}).
 *
 * @param dateDisplay      날짜 표시 방식 STANDARD · RELATIVE(관리자 게시판 기본 설정 {@code dateDisplay})
 * @param newPostHours     「새 글」 표시 기준(시간) — 관리자 설정에 없는 값이라 서버 상수 24
 * @param listChipPosition 글 목록 말머리·상태 칩 위치의 사이트 기본 ABOVE · BEFORE · BELOW(관리자 게시판 기본 설정 {@code listChipPosition}, V169) —
 *                         섞인 목록(최신글·인기글·검색·홈 가젯·팁 등)이 쓴다. 게시판 하나의 목록은 게시판 응답의 {@code chipPosition}(이미 푼 값)을 쓴다
 * @param likeHighlight    글 목록 윗줄 추천 수가 이 값 이상이면 강조(관리자 {@code listLikeHighlight}, V170). 0 = 강조 안 함
 * @param viewHighlight    글 목록 윗줄 조회 수가 이 값 이상이면 강조(관리자 {@code listViewHighlight}, V170). 0 = 강조 안 함
 * @param highlightColor   강조 색 {@code #rrggbb}(소문자, 관리자 {@code listHighlightColor}, 기본 {@code #e5484d})
 */
public record BoardDisplayView(String dateDisplay, int newPostHours, String listChipPosition, int likeHighlight, int viewHighlight, String highlightColor) {

    public static final int NEW_POST_HOURS = 24;
}

package com.community.site.dto.response;

/**
 * 공개 게시판 표시 기본값 — {@code /api/site/info} 의 {@code boardDisplay}(user-web {@code BoardDisplaySettings}).
 *
 * @param dateDisplay  날짜 표시 방식 STANDARD · RELATIVE(관리자 게시판 기본 설정 {@code dateDisplay})
 * @param newPostHours 「새 글」 표시 기준(시간) — 관리자 설정에 없는 값이라 서버 상수 24
 */
public record BoardDisplayView(String dateDisplay, int newPostHours) {

    public static final int NEW_POST_HOURS = 24;
}

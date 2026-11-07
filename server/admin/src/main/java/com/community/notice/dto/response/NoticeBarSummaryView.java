package com.community.notice.dto.response;

/**
 * 공지 띠 요약(설정 &gt; 일반 「공지 띠」 — site-notice-bar-contract).
 *
 * @param source     BOARD · CUSTOM
 * @param boardCount 공지 게시판 최근 글 수(5 · 10)
 */
public record NoticeBarSummaryView(boolean enabled, String source, int boardCount) {
}

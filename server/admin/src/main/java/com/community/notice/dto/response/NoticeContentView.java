package com.community.notice.dto.response;

/**
 * 공지 본문 — 늘 HTML(평문으로 저장된 글은 줄마다 문단으로 바꿔 내려준다). 원문을 파기한 글은 빈 글자.
 *
 * @param format HTML
 */
public record NoticeContentView(String format, String body) {
}

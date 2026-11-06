package com.community.site.dto.response;

import java.time.Instant;

/**
 * 공지 띠의 한 줄(user-web {@code SiteNoticeItem}).
 *
 * @param postId      공지 게시판 글 ID(문자열) — 직접 입력 문구면 null
 * @param title       띠에 보일 한 줄(글 제목 또는 문구)
 * @param link        누르면 갈 곳 — 글이면 {@code /posts/{id}}, 문구면 운영자가 정한 링크 또는 null
 * @param publishedAt 글 등록 시각 — 문구면 null
 */
public record SiteNoticeItemView(String postId, String title, String link, Instant publishedAt) {
}

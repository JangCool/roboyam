package com.community.site.dto;

import java.time.Instant;

/**
 * 공지 띠의 한 줄(서비스 사이 값) — 공지 게시판 글이면 글 ID·링크({@code /posts/{id}})·등록 시각이 있고, 운영자 문구면 글 ID·등록 시각이 없고 링크는 선택이다.
 *
 * @param postId      공지 게시판 글 ID(문구면 null)
 * @param title       띠에 보일 한 줄(글 제목 또는 문구)
 * @param link        누르면 갈 곳(없으면 null)
 * @param publishedAt 글 등록 시각(문구면 null)
 */
public record SiteNoticeLine(Long postId, String title, String link, Instant publishedAt) {
}

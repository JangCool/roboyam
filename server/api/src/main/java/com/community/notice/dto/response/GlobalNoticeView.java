package com.community.notice.dto.response;

import java.time.Instant;

/**
 * 게시판 글 목록 맨 위 전체 공지 한 줄(user-web {@code GlobalNoticeRow}) — 누르면 원래 공지 글({@code /posts/{id}}). docs/features/notice-admin-contract.md 「전체 공지」.
 *
 * @param createdAt 공지 글 등록 시각(줄의 날짜)
 */
public record GlobalNoticeView(String id, String title, Instant createdAt) {
}

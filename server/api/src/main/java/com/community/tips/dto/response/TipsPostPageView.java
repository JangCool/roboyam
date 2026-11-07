package com.community.tips.dto.response;

import java.util.List;

/**
 * {@code GET /api/tips/posts?board=&page=} — 최신 글 한 쪽(user-web {@code TipsPostPage}). 한 쪽 20개, 묶음(또는 고른 게시판) 글을 서버에서 최신순으로 합친다.
 *
 * @param page       지금 쪽(1부터 — 1 보다 작게 보내면 1)
 * @param totalPages 전체 쪽 수(글이 없으면 0)
 * @param totalCount 전체 글 수(공지 제외, 신고 숨긴 글 자리 포함 — 게시판 글 목록과 같은 셈)
 */
public record TipsPostPageView(List<TipsPostView> items, int page, int totalPages, long totalCount) {
}

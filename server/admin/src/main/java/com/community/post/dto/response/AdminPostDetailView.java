package com.community.post.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/posts/{id}} — 글 한 줄 · 본문 · 조치 기록 · 투표.
 *
 * @param publicUrl 공개 사이트의 이 글 주소(사이트 설정 공개 주소 + {@code /posts/{id}}) — 공개 주소가 설정돼 있지 않으면 null. 화면의 [공개 사이트에서 보기]
 * @param poll      글에 붙은 투표(집계만), 없으면 null — docs/features/post-poll-contract.md
 */
public record AdminPostDetailView(AdminPostItemView post, AdminPostContentView content, List<AdminPostEventView> events, String publicUrl, AdminPollView poll) {
}

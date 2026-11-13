package com.community.post.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/posts/{id}} — 글 한 줄 · 본문 · 조치 기록 · 투표 · 대표 썸네일.
 *
 * @param publicUrl 공개 사이트의 이 글 주소(사이트 설정 공개 주소 + {@code /posts/{id}}) — 공개 주소가 설정돼 있지 않으면 null. 화면의 [공개 사이트에서 보기]
 * @param poll      글에 붙은 투표(집계만), 없으면 null — docs/features/post-poll-contract.md. 비공개 소모임 글이면 null
 * @param contentBlocked 본문을 싣지 않은 까닭 — {@code GROUP_PRIVATE}(비공개 소모임 글 — {@code content.body}·{@code poll} 이 null), 그 밖 null(소모임 2단계)
 * @param thumbnail 대표 썸네일(V206, list-thumbnail-contract 「2단계」) — 비공개 소모임 글·원문 파기 글은 null
 */
public record AdminPostDetailView(AdminPostItemView post, AdminPostContentView content, List<AdminPostEventView> events, String publicUrl, AdminPollView poll,
                                  String contentBlocked, AdminPostThumbnailView thumbnail) {
}

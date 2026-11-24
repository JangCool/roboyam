package com.community.post.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/posts/{id}} — 글 한 줄 · 본문 · 조치 기록 · 투표 · 대표 썸네일.
 *
 * @param publicUrl 공개 사이트의 이 글 주소(사이트 설정 공개 주소 + {@code /posts/{id}}) — 공개 주소가 설정돼 있지 않으면 null. 화면의 [공개 사이트에서 보기]
 * @param poll      글에 붙은 투표(집계만), 없으면 null — docs/features/post-poll-contract.md. 비공개 소모임 글이면 null
 * @param contentBlocked 본문을 싣지 않은 까닭 — {@code GROUP_PRIVATE}(비공개 소모임 글 — {@code content.body}·{@code poll} 이 null), 그 밖 null(소모임 2단계)
 * @param thumbnail 대표 썸네일(V206, list-thumbnail-contract 「2단계」) — 비공개 소모임 글·원문 파기 글은 null
 * @param emoticons 본문(제목 제외)에 나온 로보티콘 토큰의 그림(겹치면 한 번, 판매 중단 팩 포함 — {@code AdminBodyTokenImages}). 본문이 없거나 토큰이 없으면 빈 목록
 * @param jjals     본문에 나온 짤 토큰의 표시 정보(내려간 짤은 {@code url null}·{@code blocked true}). 본문이 없거나 토큰이 없으면 빈 목록
 */
public record AdminPostDetailView(AdminPostItemView post, AdminPostContentView content, List<AdminPostEventView> events, String publicUrl, AdminPollView poll,
                                  String contentBlocked, AdminPostThumbnailView thumbnail, List<AdminBodyEmoticonView> emoticons, List<AdminBodyJjalView> jjals) {
}

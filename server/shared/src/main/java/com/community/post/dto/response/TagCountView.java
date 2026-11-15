package com.community.post.dto.response;

/**
 * 많이 달린 태그 하나(docs/features/post-tags-contract.md) — 관리자 태그 추천 {@code GET /api/admin/tags/popular} 와 매거진 {@code GET /api/tips} 의 {@code tags}.
 *
 * @param name  보이는 글자 — 같은 태그(대소문자 무시) 중 가장 많이 쓴 모양
 * @param count 그 태그가 달린 글 수(기간 안, 지운·가린 글 빼고)
 */
public record TagCountView(String name, long count) {
}

package com.community.group.dto.response;

/**
 * 할당할 수 있는 게시판(admin-web {@code GroupCategoryCandidate}) — 켜져 있는 소모임 게시판(종류 {@code GROUP}). 다른 분류에 있으면 {@code categoryId}·{@code categoryName}.
 *
 * @param groupCount 이 게시판을 쓰는 소모임 수 — 소모임 서버 단계 전까지 늘 0
 */
public record GroupCategoryCandidateView(String boardId, String slug, String name, String categoryId, String categoryName, boolean investmentNotice,
                                         long groupCount) {
}

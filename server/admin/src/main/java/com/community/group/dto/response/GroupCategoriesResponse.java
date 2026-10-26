package com.community.group.dto.response;

import java.util.List;

/**
 * {@code GET /api/admin/group-categories} — {@code PUT {id}}·{@code PUT order} 도 같은 본문 전체를 돌려준다.
 *
 * @param categories 분류 순서대로
 * @param available  할당할 수 있는 켜진 소모임 게시판(게시판 순서대로)
 */
public record GroupCategoriesResponse(List<GroupCategoryItemView> categories, List<GroupCategoryCandidateView> available) {
}

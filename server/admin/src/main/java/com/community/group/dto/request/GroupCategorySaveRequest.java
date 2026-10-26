package com.community.group.dto.request;

import java.util.List;

/**
 * {@code PUT /api/admin/group-categories/{id}} — 이름 + 이 분류의 게시판 전체 교체(보낸 순서가 할당 순서). 검사는 서비스가 한다.
 *
 * @param boardIds  할당할 게시판 ID 전부(빈 목록 가능). 다른 분류에 있던 게시판은 옮겨진다
 * @param updatedAt 화면이 불러왔을 때의 {@code updatedAt}(ISO-8601, 선택) — 서버 값이 더 최근이면 409 {@code GROUP_CATEGORY_STALE}. 없거나 비면 비교하지 않는다
 */
public record GroupCategorySaveRequest(String name, List<String> boardIds, String updatedAt) {
}

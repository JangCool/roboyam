package com.community.board.dto.request;

/**
 * {@code POST /api/admin/boards} — 일반·소모임 게시판을 만든다. 검사는 서비스가 칸 경로 키로 모은다(400 {@code VALIDATION} {@code fields.name|slug|description|layout|kind|groupCategoryId}).
 *
 * @param kind            {@code GENERAL}(비우면 이것) · {@code GROUP}. 그 밖은 400 {@code fields.kind}
 * @param groupCategoryId 소모임 분류(소모임 게시판만, 선택) — 일반 게시판에 보내거나 없는 분류면 400 {@code fields.groupCategoryId}. 보내려면 {@code group.settings} 도 필요하다
 */
public record BoardCreateRequest(String name, String slug, String description, String layout, String kind, String groupCategoryId) {
}

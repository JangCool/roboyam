package com.community.group.dto.request;

/** {@code POST /api/admin/group-categories} — 이름만(맨 끝 순서, 게시판 0개). 검사는 서비스가 한다(400 {@code fields.name}) */
public record GroupCategoryCreateRequest(String name) {
}

package com.community.board.dto.request;

/** {@code POST /api/admin/boards} — 일반 게시판만 만든다. 검사는 서비스가 칸 경로 키로 모은다(400 {@code VALIDATION} {@code fields.name|slug|description|layout}) */
public record BoardCreateRequest(String name, String slug, String description, String layout) {
}

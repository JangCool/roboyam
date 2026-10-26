package com.community.group.dto.request;

/** {@code PUT /api/groups/{id}} — 모임장의 정보 수정(모든 칸 — 분류·게시판은 못 바꾼다) */
public record GroupUpdateRequest(String name, String description, String joinType, Integer capacity, Boolean offline) {
}

package com.community.group.dto.request;

/**
 * {@code PUT /api/groups/{id}} — 모임장의 정보 수정(모든 칸 — 분류·게시판은 못 바꾼다).
 *
 * @param imageFileId 대표 이미지(V154) — 지금 이 소모임의 파일 ID 면 그대로, 새로 올린 파일이면 바꾸고, null(또는 칸 없음)이면 지운다. PUT 은 모든 칸을 보내는 관례라 칸이 빠진 요청도 「지움」으로 본다
 */
public record GroupUpdateRequest(String name, String description, String joinType, Integer capacity, Boolean offline, String imageFileId) {
}

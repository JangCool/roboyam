package com.community.group.dto.response;

/**
 * {@code POST /api/groups/{id}/posts}(201) — 새 비공개 소모임 글.
 *
 * @param pointsEarned 이 글로 받은 포인트(지급이 없으면 0 — 일반 글과 하루 상한을 함께 쓴다)
 */
public record GroupPostWriteResultView(String id, long pointsEarned) {
}

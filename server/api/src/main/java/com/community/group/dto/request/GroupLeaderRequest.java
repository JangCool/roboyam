package com.community.group.dto.request;

/** {@code POST /api/groups/{id}/leader} — 새 모임장(내가 아닌 지금 멤버) */
public record GroupLeaderRequest(String userId) {
}

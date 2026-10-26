package com.community.group.dto.request;

/**
 * {@code POST /api/groups/{id}/join} — 가입·신청.
 *
 * @param message    한마디(선택, 100자)
 * @param offlineAck 오프라인 모임의 개인 책임 고지 동의(오프라인 모임이면 true 여야 한다)
 */
public record GroupJoinRequest(String message, Boolean offlineAck) {
}

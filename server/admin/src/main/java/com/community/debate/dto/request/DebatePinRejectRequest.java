package com.community.debate.dto.request;

/** {@code POST /api/admin/debates/{postId}/pin-requests/{requestId}/reject} — {@code reason} 거절 사유(선택, 0~100자) */
public record DebatePinRejectRequest(String reason) {
}

package com.community.highlight.dto.request;

/** {@code POST /api/admin/highlights/{id}/pin-requests/{requestId}/reject} — {@code reason} 거절 사유(선택, 0~100자) */
public record HighlightPinRejectRequest(String reason) {
}

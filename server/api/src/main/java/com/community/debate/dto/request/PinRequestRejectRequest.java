package com.community.debate.dto.request;

/** {@code POST /api/pin-requests/{id}/reject} — {@code reason} 거절 사유(선택, 0~100자, 앞뒤 공백을 떼고 비면 없음) */
public record PinRequestRejectRequest(String reason) {
}

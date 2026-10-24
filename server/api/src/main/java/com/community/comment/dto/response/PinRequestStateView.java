package com.community.comment.dto.response;

/**
 * 내 의견의 고정 요청 상태(댓글 줄 {@code pinRequest}) — 내가 쓴 의견 원댓글에만, 가장 최근 요청이 대기 중이거나 거절됐을 때. 승인됐거나(그 의견은 {@code pinned}) 요청이 없으면 칸이 null.
 *
 * @param requestId    요청 ID(취소는 {@code DELETE /api/comments/{commentId}/pin-request})
 * @param status       {@code PENDING} · {@code REJECTED}
 * @param rejectReason 거절 사유(없으면 null)
 */
public record PinRequestStateView(String requestId, String status, String rejectReason) {
}

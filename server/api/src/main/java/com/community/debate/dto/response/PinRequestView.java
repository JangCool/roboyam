package com.community.debate.dto.response;

import java.time.Instant;

/**
 * 의견 고정 요청 한 건(보내기·승인·거절 응답).
 *
 * @param status       PENDING · APPROVED · REJECTED
 * @param rejectReason 거절 사유(없으면 null)
 * @param decidedAt    승인·거절 시각(대기면 null)
 */
public record PinRequestView(String id, String commentId, String postId, String status, String rejectReason, Instant createdAt, Instant decidedAt) {
}

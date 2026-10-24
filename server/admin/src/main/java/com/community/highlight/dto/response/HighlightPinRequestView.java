package com.community.highlight.dto.response;

import java.time.Instant;

/**
 * 하이라이트 의견 고정 요청 한 줄(관리자 — 대기 목록·승인·거절 응답).
 *
 * @param status       PENDING · APPROVED · REJECTED
 * @param opinion      대상 의견(지금 모습 — 지운 의견이면 null)
 * @param requesterId  요청한 회원 ID(= 의견을 쓴 회원)
 * @param rejectReason 거절 사유(없으면 null)
 */
public record HighlightPinRequestView(String id, String commentId, String status, HighlightOpinionView opinion, String requesterId, String rejectReason,
                                      Instant createdAt, Instant decidedAt) {
}

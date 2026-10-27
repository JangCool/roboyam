package com.community.payment.dto.response;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.PgCancelStatus;
import java.time.Instant;

/**
 * 취소 대기열 한 줄 — admin-web {@code PaymentCancelView} 모양. 마지막 오류는 비밀값을 가린 서버 글자, 운영자는 이름만.
 *
 * @param nextAttemptAt 다음 시도 시각 — 보낼 차례를 기다릴 때(PENDING)만
 * @param reason        내부 사유(회원에게 보이지 않는다 — 포트원에도 보내지 않는다)
 * @param requestedBy   요청한 운영자 이름(시스템이면 null)
 * @param resolvedBy    「다시 시도」·「수동 처리 완료」를 한 운영자 이름
 */
public record PaymentCancelView(String id, long amount, CancelKind kind, CancelStatus status, PgCancelStatus pgStatus, int attempts, Instant nextAttemptAt,
                                String lastError, String reason, String requestedBy, String resolvedBy, String resolveNote, Instant createdAt, Instant completedAt) {
}

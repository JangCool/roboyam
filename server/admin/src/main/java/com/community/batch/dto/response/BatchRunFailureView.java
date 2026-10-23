package com.community.batch.dto.response;

import java.time.Instant;

/**
 * 실행의 건별 실패 한 줄({@code batch_run_failures}).
 *
 * @param stepLabel       단계 이름(서버 글)
 * @param targetType      대상 종류 코드(예 {@code POINT_LOT})
 * @param targetLabel     대상 종류 이름(서버 글 — 번들 {@code batch.target.<코드>}, 없으면 코드)
 * @param message         서버가 개인정보·비밀값을 가린 글
 * @param attempts        시도 횟수 — 「실패 건만 다시」에서 또 실패하면 원래 줄은 미해결인 채 +1, 새 실행에는 새 줄(원래 + 1)이 생긴다(확정 규칙 4)
 * @param resolvedByRunId 해결한 실행 — 해결 안 됐으면 null
 */
public record BatchRunFailureView(String failureId, String stepName, String stepLabel, String targetType, String targetLabel, String targetId, String errorCode,
                                  String message, int attempts, boolean resolved, String resolvedByRunId, Instant occurredAt) {
}

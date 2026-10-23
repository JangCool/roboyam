package com.community.batch.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 실행 상세 — {@code GET /api/admin/batch-logs/{logId}}. 로그 한 줄({@link BatchLogView})의 칸에 아래를 더한 모양이다(docs/features/batch-contract.md 「0-2 보강」).
 *
 * @param scheduledAt            예약 실행의 예정 시각(수동이면 null)
 * @param summary                결과 요약(성공·건너뜀) — 서버 글
 * @param errorMessage           실패 사유(가린 글). 실패가 아니면 null
 * @param nodeId                 돌린 서버(DB 배치는 null)
 * @param params                 실행 파라미터(없으면 빈 객체)
 * @param readCount              읽은 건수 — 주 단계(DB 배치는 null)
 * @param processedCount         처리 건수 — 주 단계
 * @param skippedCount           건너뛴 건수 — 주 단계(DB 배치는 null)
 * @param checkpoint             마지막으로 끝낸 덩어리의 정렬 키
 * @param resumedFromRunId       이 실행이 다시 돌린 원래 실행
 * @param resumedByRunIds        이 실행을 다시 돌린 실행들
 * @param requestId              이 실행을 만든 요청(예약 실행은 null)
 * @param steps                  단계(차례대로). DB 배치는 실행 줄로 만든 한 단계
 * @param unresolvedFailureCount 아직 해결하지 않은 실패 수
 * @param canResume              「실패 지점부터 다시」를 할 수 있나 — 실패로 끝났고 체크포인트가 있고 작업이 지원하며, 이어 받은(RESUME) 실행이 아직 없을 때(확정 규칙 3)
 * @param canRetryFailures       「실패 건만 다시」를 할 수 있나 — 미해결 실패가 있고 Java 작업일 때
 * @param pendingRequest         이 실행에 대한 다시 실행 요청이 대기·진행 중이면 그 요청
 */
public record BatchRunDetailView(String logId, String jobName, String displayName, String source, String trigger, String operatorName, Instant startedAt,
                                 Instant finishedAt, String status, Long affectedCount, String message, String runMode, Long failedCount, Instant scheduledAt,
                                 String summary, String errorMessage, String nodeId, Map<String, String> params, Long readCount, Long processedCount,
                                 Long skippedCount, String checkpoint, String resumedFromRunId, List<String> resumedByRunIds, String requestId,
                                 List<BatchRunStepView> steps, long unresolvedFailureCount, boolean canResume, boolean canRetryFailures,
                                 BatchRunRequestView pendingRequest) {
}

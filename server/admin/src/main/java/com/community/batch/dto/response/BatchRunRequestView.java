package com.community.batch.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 「지금 실행」·「실패 지점부터 다시」·「실패 건만 다시」가 넣은 요청 한 줄({@code batch_run_requests}) — 배치를 켠 서버({@code roboyam.batch.enabled})가 집어 실행한다.
 * 화면은 요청 뒤 이 줄을 다시 읽어 상태를 따라간다({@code GET /api/admin/batch-requests/{requestId}}).
 *
 * @param kind     {@code NOW} · {@code RESUME} · {@code RETRY_FAILED}
 * @param status   {@code PENDING} → {@code RUNNING} → {@code DONE} 또는 {@code REJECTED}
 * @param runId    실행이 시작되면 그 실행 로그 번호. 아직 안 집었거나 거절이면 null
 * @param params   요청에 실은 실행 파라미터 — 없으면 빈 객체
 * @param steps    고른 단계 — 모두 돌리면 null
 * @param message  거절 사유 등 — 서버가 요청 언어로 푼 글. 없으면 null
 */
public record BatchRunRequestView(String requestId, String jobName, String kind, String status, String runId, Map<String, String> params, List<String> steps,
                                  Instant requestedAt, Instant startedAt, Instant finishedAt, String message, String operatorName) {
}

package com.community.batch.dto.response;

import java.time.Instant;

/**
 * 실행 상세의 단계 한 줄({@code batch_run_steps}). DB 배치는 실행 줄로 만든 한 단계(읽음·실패·건너뜀은 null).
 *
 * @param label      서버가 요청 언어로 푼 이름
 * @param order      1 부터
 * @param status     {@code PENDING} · {@code RUNNING} · {@code OK} · {@code FAIL} · {@code SKIP}
 * @param checkpoint 그 단계가 마지막으로 끝낸 덩어리의 정렬 키 — 없으면 null
 */
public record BatchRunStepView(String name, String label, int order, String status, Long readCount, Long processedCount, Long failedCount, Long skippedCount,
                               Instant startedAt, Instant finishedAt, String checkpoint) {
}

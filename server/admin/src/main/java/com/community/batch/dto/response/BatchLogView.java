package com.community.batch.dto.response;

import java.time.Instant;

/**
 * 배치 로그 목록의 한 줄. {@code message} 는 성공·건너뜀이면 결과 요약, 실패면 사유다. {@code operatorName} 은 수동 실행의 운영자(예약이거나 운영자가 지워졌으면 null).
 *
 * @param logId         실행 로그 ID(문자열)
 * @param jobName       배치 키
 * @param trigger       {@code SCHEDULED} · {@code MANUAL}
 * @param status        {@code RUNNING} · {@code OK} · {@code FAIL} · {@code SKIP}
 * @param affectedCount 처리 건수. 끝나지 않았거나 실패면 null
 */
public record BatchLogView(String logId, String jobName, String displayName, String source, String trigger, String operatorName, Instant startedAt,
                           Instant finishedAt, String status, Long affectedCount, String message) {
}

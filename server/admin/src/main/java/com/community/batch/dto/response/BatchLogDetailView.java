package com.community.batch.dto.response;

import java.time.Instant;

/**
 * 배치 로그 상세. 목록의 칸에 더해 요약({@code summary})과 실패 사유({@code errorMessage})를 따로 주고, 예약 시각·실행한 서버를 보인다.
 *
 * @param scheduledAt  예약 실행의 예약 시각(수동이면 null)
 * @param summary      결과 요약(성공·건너뜀). 없으면 null
 * @param errorMessage 실패 사유(예외 이름 + 첫 줄, 민감값은 가려져 있다). 실패가 아니면 null
 * @param nodeId       실행한 서버의 NODE_ID
 */
public record BatchLogDetailView(String logId, String jobName, String displayName, String source, String trigger, String operatorName, Instant scheduledAt,
                                 Instant startedAt, Instant finishedAt, String status, Long affectedCount, String message, String summary,
                                 String errorMessage, Integer nodeId) {
}

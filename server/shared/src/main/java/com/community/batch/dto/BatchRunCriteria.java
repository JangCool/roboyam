package com.community.batch.dto;

import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.BatchTriggerType;
import java.time.Instant;

/**
 * 실행 로그 조회 조건. 비어 있는(null) 조건은 걸지 않는다.
 *
 * @param startedFrom 시작 시각의 하한(포함)
 * @param startedTo   시작 시각의 상한(포함)
 */
public record BatchRunCriteria(String jobKey, BatchRunStatus status, BatchTriggerType triggerType, Instant startedFrom, Instant startedTo, int page, int size) {
}

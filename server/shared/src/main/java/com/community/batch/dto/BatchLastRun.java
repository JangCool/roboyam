package com.community.batch.dto;

import java.time.Instant;

/** 배치 목록에 보이는 마지막 실행 요약 — 실행이 시작·끝날 때 {@code batch_jobs.last_*} 에 적는다. 끝나지 않은 실행은 {@code RUNNING} 이다 */
public record BatchLastRun(Instant at, String status, Long affectedCount, String messageKey, String messageArgs, String error) {
}

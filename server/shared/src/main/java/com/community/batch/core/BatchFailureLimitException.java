package com.community.batch.core;

/**
 * 한 실행의 실패 건수가 작업별 한도({@code batch_params(job_key, 'failure_limit')}, 기본 {@value BatchRunRecorder#DEFAULT_FAILURE_LIMIT})를 넘었다 — 실행기가 실행을 FAIL
 * (문구 키 {@value #MESSAGE_KEY})로 멈춘다. 대량 장애에도 실패 기록이 무한히 쌓이지 않게 한다(15-3 성능 규칙).
 */
public final class BatchFailureLimitException extends RuntimeException {

    public static final String MESSAGE_KEY = "batch.error.failure-limit";

    private final int limit;

    public BatchFailureLimitException(int limit) {
        super("실패 건수가 한도(" + limit + ")를 넘었다");
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}

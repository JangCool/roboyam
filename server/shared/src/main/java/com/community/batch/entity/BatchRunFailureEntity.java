package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 실행의 건별 실패 한 줄 — V61 의 {@code batch_run_failures}. 넣기는 {@link #record}({@code com.community.batch.core.BatchFailureRecorder} 가 덩어리 일과 다른
 * 트랜잭션으로 넣고 바로 내보낸다), 해결 표시·시도 횟수는 조건부 UPDATE(QueryDSL — {@code NOT resolved} 일 때만).
 * 문구는 기록할 때 비밀값·개인정보를 가렸다.
 */
@Entity
@Table(name = "batch_run_failures")
public class BatchRunFailureEntity {

    @Id
    private Long id;

    private Long runId;

    private String stepName;

    private String targetType;

    private String targetId;

    private String errorCode;

    private String message;

    private int attempts;

    private boolean resolved;

    private Long resolvedByRunId;

    private Instant resolvedAt;

    private Instant occurredAt;

    protected BatchRunFailureEntity() {
    }

    /** 실패 한 줄(미해결) — 대상·문구는 부르는 쪽이 길이에 맞춰 가렸다 */
    public static BatchRunFailureEntity record(long id, long runId, String stepName, String targetType, String targetId, String errorCode, String message, int attempts,
                                               Instant occurredAt) {
        BatchRunFailureEntity f = new BatchRunFailureEntity();
        f.id = id;
        f.runId = runId;
        f.stepName = stepName;
        f.targetType = targetType;
        f.targetId = targetId;
        f.errorCode = errorCode;
        f.message = message;
        f.attempts = attempts;
        f.resolved = false;
        f.occurredAt = occurredAt;
        return f;
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public String getStepName() {
        return stepName;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isResolved() {
        return resolved;
    }

    public Long getResolvedByRunId() {
        return resolvedByRunId;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

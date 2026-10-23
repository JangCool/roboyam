package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 실행의 건별 실패 한 줄 — V61 의 {@code batch_run_failures}. <b>읽기 전용 매핑</b>이다(쓰는 것은 {@code com.community.batch.core.BatchFailureRecorder}, JDBC).
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

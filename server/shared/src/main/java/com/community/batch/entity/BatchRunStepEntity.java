package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 실행의 단계 한 줄 — V61 의 {@code batch_run_steps}. <b>읽기 전용 매핑</b>이다(쓰는 것은 {@code com.community.batch.core.BatchRunRecorder}, JDBC).
 * 건수·체크포인트는 덩어리를 저장 확정할 때마다 한 번 고쳐진다.
 */
@Entity
@Table(name = "batch_run_steps")
@IdClass(BatchRunStepId.class)
public class BatchRunStepEntity {

    @Id
    private Long runId;

    @Id
    private String stepName;

    private short stepOrder;

    @Enumerated(EnumType.STRING)
    private BatchStepStatus status;

    private long readCount;

    private long processedCount;

    private long failedCount;

    private long skippedCount;

    private Instant startedAt;

    private Instant finishedAt;

    private String checkpoint;

    protected BatchRunStepEntity() {
    }

    public Long getRunId() {
        return runId;
    }

    public String getStepName() {
        return stepName;
    }

    public short getStepOrder() {
        return stepOrder;
    }

    public BatchStepStatus getStatus() {
        return status;
    }

    public long getReadCount() {
        return readCount;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public long getSkippedCount() {
        return skippedCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getCheckpoint() {
        return checkpoint;
    }
}

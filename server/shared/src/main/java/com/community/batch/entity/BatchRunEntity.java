package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 배치 실행 로그 한 줄 — V10__batches.sql 의 {@code batch_runs}. 시작할 때 {@code RUNNING} 으로 넣고 끝나면 {@link #finish} 로 결과를 채운다.
 * 결과 요약은 문구 키 + 인자(JSON 배열)이고, 실패 사유({@code errorMessage})는 호출하는 쪽이 민감값을 가려 넘긴다.
 */
@Entity
@Table(name = "batch_runs")
public class BatchRunEntity {

    @Id
    private Long id;

    private String jobKey;

    @Enumerated(EnumType.STRING)
    private BatchRunStatus status;

    @Enumerated(EnumType.STRING)
    private BatchTriggerType triggerType;

    /** 예약 시각(슬롯) — 예약 실행만 있다 */
    private Instant scheduledAt;

    private Instant startedAt;

    private Instant finishedAt;

    private Long affectedCount;

    private String messageKey;

    private String messageArgs;

    private String errorMessage;

    /** 「지금 실행」을 누른 운영자 — 수동 실행만 있다 */
    private Long operatorId;

    /** 실행한 서버의 {@code NODE_ID} */
    private Integer nodeId;

    protected BatchRunEntity() {
    }

    /** 시작 — 상태 {@code RUNNING} */
    public static BatchRunEntity start(long id, String jobKey, BatchTriggerType triggerType, Instant scheduledAt, Instant startedAt, Long operatorId, int nodeId) {
        BatchRunEntity run = new BatchRunEntity();
        run.id = id;
        run.jobKey = jobKey;
        run.status = BatchRunStatus.RUNNING;
        run.triggerType = triggerType;
        run.scheduledAt = scheduledAt;
        run.startedAt = startedAt;
        run.operatorId = operatorId;
        run.nodeId = nodeId;
        return run;
    }

    /** 끝낸다 — 성공·건너뜀·실패 중 하나로. 실패는 {@code errorMessage} 에 사유(민감값을 가린)를 싣는다 */
    public void finish(BatchRunStatus status, Instant finishedAt, Long affectedCount, String messageKey, String messageArgs, String errorMessage) {
        this.status = status;
        this.finishedAt = finishedAt;
        this.affectedCount = affectedCount;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
        this.errorMessage = errorMessage;
    }

    public Long getId() {
        return id;
    }

    public String getJobKey() {
        return jobKey;
    }

    public BatchRunStatus getStatus() {
        return status;
    }

    public BatchTriggerType getTriggerType() {
        return triggerType;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getAffectedCount() {
        return affectedCount;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getMessageArgs() {
        return messageArgs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public Integer getNodeId() {
        return nodeId;
    }
}

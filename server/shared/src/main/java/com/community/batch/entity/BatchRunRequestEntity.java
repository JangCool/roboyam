package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 실행 요청 한 줄 — V61 의 {@code batch_run_requests}. <b>읽기 전용 매핑</b>이다(넣고 집고 닫는 것은 {@code com.community.batch.core.BatchRequestQueue}, JDBC).
 */
@Entity
@Table(name = "batch_run_requests")
public class BatchRunRequestEntity {

    @Id
    private Long id;

    private String jobKey;

    @Enumerated(EnumType.STRING)
    private BatchRequestKind kind;

    @Enumerated(EnumType.STRING)
    private BatchRequestStatus status;

    /** JSON 객체 — 없으면 null */
    private String params;

    /** 고른 단계의 JSON 배열 — 모두면 null */
    private String steps;

    private Long sourceRunId;

    private Long runId;

    private Long operatorId;

    private Instant requestedAt;

    private Instant startedAt;

    private Instant finishedAt;

    private String messageKey;

    private String messageArgs;

    private Integer nodeId;

    protected BatchRunRequestEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getJobKey() {
        return jobKey;
    }

    public BatchRequestKind getKind() {
        return kind;
    }

    public BatchRequestStatus getStatus() {
        return status;
    }

    public String getParams() {
        return params;
    }

    public String getSteps() {
        return steps;
    }

    public Long getSourceRunId() {
        return sourceRunId;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getMessageArgs() {
        return messageArgs;
    }

    public Integer getNodeId() {
        return nodeId;
    }
}

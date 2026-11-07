package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 실행 요청 한 줄 — V61 의 {@code batch_run_requests}. 넣기는 {@link #pending}({@code com.community.batch.core.BatchRequestQueue} 가 넣고 바로 내보낸다 —
 * 끝나지 않은 요청 유일 색인 위반이 그 자리에서 난다), 집기·닫기는 조건부 UPDATE(QueryDSL). 「없을 때만 넣기」는 {@code ON CONFLICT DO NOTHING}(native).
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

    /** 대기 요청 한 줄 */
    public static BatchRunRequestEntity pending(long id, String jobKey, BatchRequestKind kind, String params, String steps, Long sourceRunId, Long operatorId,
                                                Instant requestedAt) {
        BatchRunRequestEntity r = new BatchRunRequestEntity();
        r.id = id;
        r.jobKey = jobKey;
        r.kind = kind;
        r.status = BatchRequestStatus.PENDING;
        r.params = params;
        r.steps = steps;
        r.sourceRunId = sourceRunId;
        r.operatorId = operatorId;
        r.requestedAt = requestedAt;
        return r;
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

package com.community.batch.entity;

import jakarta.persistence.Column;
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

    /** 실행한 서버의 {@code NODE_ID}. DB 배치(저장 프로시저)가 만든 줄에는 없다 */
    private Integer nodeId;

    /** 어디서 돈 배치인가. 앱이 만드는 줄은 {@code JAVA}, DB 프로시저가 만든 줄(V20 이후)은 {@code POSTGRESQL} */
    @Enumerated(EnumType.STRING)
    private BatchSource source;

    /** 알림 감시자가 이 줄을 처리한 시각(V40) — 끝난 줄 중 비어 있는 것만 감시자가 본다. 읽기 전용 매핑이다(채우는 것은 감시자의 조건부 UPDATE) */
    @Column(insertable = false, updatable = false)
    private Instant notifiedAt;

    protected BatchRunEntity() {
    }

    /** 시작 — 상태 {@code RUNNING}. 앱(Java 배치)이 만드는 줄이다 — DB 배치의 줄은 프로시저가 직접 넣는다 */
    public static BatchRunEntity start(long id, String jobKey, BatchTriggerType triggerType, Instant scheduledAt, Instant startedAt, Long operatorId, int nodeId) {
        BatchRunEntity run = new BatchRunEntity();
        run.id = id;
        run.source = BatchSource.JAVA;
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

    public BatchSource getSource() {
        return source;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }
}

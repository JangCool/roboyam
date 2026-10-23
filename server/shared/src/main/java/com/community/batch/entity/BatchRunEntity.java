package com.community.batch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 배치 실행 로그 한 줄 — V10__batches.sql 의 {@code batch_runs}(+ V20 {@code source}, V40 {@code notified_at}, V61 실행 방식·파라미터·건수·이어 받기·체크포인트·요청).
 * <b>읽기 전용 매핑</b>이다 — 줄을 넣고 고치는 것은 Java 배치의 실행 기록기({@code com.community.batch.core.BatchRunRecorder}, JDBC)와 DB 배치 프로시저({@code batch_run_begin/close})다.
 * 결과 요약은 문구 키 + 인자(JSON 배열)이고, 실패 사유({@code errorMessage})는 기록하는 쪽이 민감값을 가려 넣는다.
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

    /** 처리 건수(Java 배치는 주 단계의 처리 건수) */
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

    /** 실행 방식(V61) — DB 배치 줄은 늘 {@code NORMAL} */
    @Enumerated(EnumType.STRING)
    private BatchRunMode runMode;

    /** 실행 파라미터(JSON 객체, V61). 없으면 null */
    private String params;

    /** 읽은 건수 — 주 단계(V61). DB 배치는 null */
    private Long readCount;

    /** 실패 건수 — 단계 합(V61). DB 배치는 null */
    private Long failedCount;

    /** 건너뛴 건수 — 주 단계(V61). DB 배치는 null */
    private Long skippedCount;

    /** 이 실행이 다시 돌린 원래 실행(V61) */
    private Long resumedFromRunId;

    /** 마지막으로 끝낸 덩어리의 정렬 키(V61) */
    private String checkpoint;

    /** 이 실행을 만든 요청(V61) — 예약 실행은 null */
    private Long requestId;

    protected BatchRunEntity() {
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

    public BatchRunMode getRunMode() {
        return runMode;
    }

    public String getParams() {
        return params;
    }

    public Long getReadCount() {
        return readCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public Long getSkippedCount() {
        return skippedCount;
    }

    public Long getResumedFromRunId() {
        return resumedFromRunId;
    }

    public String getCheckpoint() {
        return checkpoint;
    }

    public Long getRequestId() {
        return requestId;
    }
}

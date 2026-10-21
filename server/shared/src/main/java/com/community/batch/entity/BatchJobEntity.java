package com.community.batch.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 배치 하나의 운영 설정(사용 여부·cron)과 마지막 실행 요약 — V10__batches.sql 의 {@code batch_jobs}. 배치의 정의(이름·설명·기본값)는 코드(카탈로그)가 원천이고
 * 이 행은 서버가 뜰 때 카탈로그에 맞춰 만들어진다. 마지막 실행 요약({@code last_*})은 실행 기록이 시작·끝날 때 조건부 UPDATE 로 고치므로
 * 이 엔티티에 도메인 메서드로 두지 않는다(운영자의 동시 변경을 덮어쓰지 않으려고).
 */
@Entity
@Table(name = "batch_jobs")
public class BatchJobEntity {

    @Id
    private String jobKey;

    private boolean enabled;

    private String cron;

    private Instant lastRunAt;

    private String lastStatus;

    private Long lastAffectedCount;

    private String lastMessageKey;

    private String lastMessageArgs;

    private String lastError;

    /** 마지막으로 설정을 바꾼 운영자. 서버가 처음 넣은 행은 null */
    private Long updatedBy;

    private Instant createdAt;

    private Instant updatedAt;

    protected BatchJobEntity() {
    }

    /** 카탈로그 기본값으로 처음 넣는다 */
    public static BatchJobEntity create(String jobKey, boolean enabled, String cron, Instant now) {
        BatchJobEntity job = new BatchJobEntity();
        job.jobKey = jobKey;
        job.enabled = enabled;
        job.cron = cron;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void changeEnabled(boolean enabled, long operatorId, Instant now) {
        this.enabled = enabled;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public void changeCron(String cron, long operatorId, Instant now) {
        this.cron = cron;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public String getJobKey() {
        return jobKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCron() {
        return cron;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public Long getLastAffectedCount() {
        return lastAffectedCount;
    }

    public String getLastMessageKey() {
        return lastMessageKey;
    }

    public String getLastMessageArgs() {
        return lastMessageArgs;
    }

    public String getLastError() {
        return lastError;
    }
}

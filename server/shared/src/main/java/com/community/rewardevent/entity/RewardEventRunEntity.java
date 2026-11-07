package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 지급 실행 — shared V163 {@code reward_event_runs}. 넣기는 저장소, 진행·합계·끝은 저장소의 DML(덩어리마다 그 실행 행을 잠근 트랜잭션이 고친다 — 서버 여러 대여도 한 곳만).
 * 선착순 자동(AUTO)은 날마다 한 줄을 「없으면 넣기」로 만든다(저장소 — 지급 트랜잭션의 세이브포인트 안이라 JDBC).
 */
@Entity
@Table(name = "reward_event_runs")
public class RewardEventRunEntity {

    @Id
    private Long id;
    private Long eventId;
    @Enumerated(EnumType.STRING)
    private RewardRunKind kind;
    @Enumerated(EnumType.STRING)
    private RewardRunStatus status;
    @Enumerated(EnumType.STRING)
    private RewardRunPhase phase;
    private Long cursorUserId;
    private Long operatorId;
    private String idempotencyKey;
    private LocalDate day;
    private int targetCount;
    private int doneCount;
    private int success;
    private int failed;
    private int skipped;
    private long points;
    private int items;
    private String reason;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant finishedAt;

    protected RewardEventRunEntity() {
    }

    /** 나눠 처리할 실행(지급·재시도 — RUNNING) */
    public static RewardEventRunEntity running(long id, long eventId, RewardRunKind kind, RewardRunPhase phase, long operatorId, String idempotencyKey, int targetCount,
                                               Instant now) {
        RewardEventRunEntity r = new RewardEventRunEntity();
        r.id = id;
        r.eventId = eventId;
        r.kind = kind;
        r.status = RewardRunStatus.RUNNING;
        r.phase = phase;
        r.operatorId = operatorId;
        r.idempotencyKey = idempotencyKey;
        r.targetCount = targetCount;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    /** 한 번에 끝난 실행(회수 — DONE) */
    public static RewardEventRunEntity done(long id, long eventId, RewardRunKind kind, long operatorId, int targetCount, int success, int failed, int skipped, long points,
                                            int items, String reason, Instant now) {
        RewardEventRunEntity r = new RewardEventRunEntity();
        r.id = id;
        r.eventId = eventId;
        r.kind = kind;
        r.status = RewardRunStatus.DONE;
        r.phase = RewardRunPhase.LINES;
        r.operatorId = operatorId;
        r.targetCount = targetCount;
        r.doneCount = targetCount;
        r.success = success;
        r.failed = failed;
        r.skipped = skipped;
        r.points = points;
        r.items = items;
        r.reason = reason;
        r.createdAt = now;
        r.updatedAt = now;
        r.finishedAt = now;
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public RewardRunKind getKind() {
        return kind;
    }

    public RewardRunStatus getStatus() {
        return status;
    }

    public RewardRunPhase getPhase() {
        return phase;
    }

    public Long getCursorUserId() {
        return cursorUserId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public int getDoneCount() {
        return doneCount;
    }

    public int getSuccess() {
        return success;
    }

    public int getFailed() {
        return failed;
    }

    public int getSkipped() {
        return skipped;
    }

    public long getPoints() {
        return points;
    }

    public int getItems() {
        return items;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}

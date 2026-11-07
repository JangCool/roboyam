package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 받는 사람 — shared V163 {@code reward_event_recipients}(이벤트당 한 사람 한 번). 넣기는 저장소({@code persist} 직후 내보내고 문맥에서 뗀다), 상태·대기열·회수 칸은 저장소의 DML —
 * 줄 처리(세이브포인트)와 섞여도 문맥에 남아 나중에 내보내질 변경이 없게(GroupBuySavepoint 의 약속과 같다).
 */
@Entity
@Table(name = "reward_event_recipients")
public class RewardEventRecipientEntity {

    @Id
    private Long id;
    private Long eventId;
    private Long tierId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private RewardRecipientMethod method;
    @Enumerated(EnumType.STRING)
    private RewardRecipientStatus status;
    private Instant selectedAt;
    private Long selectedBy;
    private Long drawId;
    private Long queuedRunId;
    private String revokeReason;
    private Instant revokedAt;
    private Long revokedBy;

    protected RewardEventRecipientEntity() {
    }

    /** 새 받는 사람(대기) — {@code queuedRunId} 는 일괄 지급이 대상 줄을 만들 때만 */
    public static RewardEventRecipientEntity pending(long id, long eventId, long tierId, long userId, RewardRecipientMethod method, Instant selectedAt, Long selectedBy,
                                                     Long drawId, Long queuedRunId) {
        RewardEventRecipientEntity r = new RewardEventRecipientEntity();
        r.id = id;
        r.eventId = eventId;
        r.tierId = tierId;
        r.userId = userId;
        r.method = method;
        r.status = RewardRecipientStatus.PENDING;
        r.selectedAt = selectedAt;
        r.selectedBy = selectedBy;
        r.drawId = drawId;
        r.queuedRunId = queuedRunId;
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getTierId() {
        return tierId;
    }

    public Long getUserId() {
        return userId;
    }

    public RewardRecipientMethod getMethod() {
        return method;
    }

    public RewardRecipientStatus getStatus() {
        return status;
    }

    public Instant getSelectedAt() {
        return selectedAt;
    }

    public Long getSelectedBy() {
        return selectedBy;
    }

    public Long getDrawId() {
        return drawId;
    }

    public Long getQueuedRunId() {
        return queuedRunId;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }
}

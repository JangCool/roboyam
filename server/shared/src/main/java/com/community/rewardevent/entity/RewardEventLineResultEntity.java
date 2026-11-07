package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 받는 사람 × 보상 줄의 결과 — shared V163 {@code reward_event_line_results}. id 가 원장(대상 {@code REWARD_EVENT_RESULT})·보유 줄({@code user_items.grant_result_id})·로보티콘 받기 줄
 * ({@code emoticon_acquisitions.reward_result_id})의 대상이다. 넣기는 저장소, 결과 칸은 저장소의 DML(받는 사람과 같은 까닭).
 */
@Entity
@Table(name = "reward_event_line_results")
public class RewardEventLineResultEntity {

    @Id
    private Long id;
    private Long eventId;
    private Long recipientId;
    private Long lineId;
    private Long userId;
    @Enumerated(EnumType.STRING)
    private RewardKind kind;
    @Enumerated(EnumType.STRING)
    private RewardLineResultStatus status;
    @Enumerated(EnumType.STRING)
    private RewardLineReason reason;
    private Instant at;
    private int points;
    private int items;
    private Integer revokedPoints;
    private Instant deliveredAt;
    private Long deliveredBy;
    private String deliveredMemo;

    protected RewardEventLineResultEntity() {
    }

    public static RewardEventLineResultEntity pending(long id, long eventId, long recipientId, long lineId, long userId, RewardKind kind) {
        RewardEventLineResultEntity r = new RewardEventLineResultEntity();
        r.id = id;
        r.eventId = eventId;
        r.recipientId = recipientId;
        r.lineId = lineId;
        r.userId = userId;
        r.kind = kind;
        r.status = RewardLineResultStatus.PENDING;
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public Long getLineId() {
        return lineId;
    }

    public Long getUserId() {
        return userId;
    }

    public RewardKind getKind() {
        return kind;
    }

    public RewardLineResultStatus getStatus() {
        return status;
    }

    public RewardLineReason getReason() {
        return reason;
    }

    public Instant getAt() {
        return at;
    }

    public int getPoints() {
        return points;
    }

    public int getItems() {
        return items;
    }

    public Integer getRevokedPoints() {
        return revokedPoints;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Long getDeliveredBy() {
        return deliveredBy;
    }

    public String getDeliveredMemo() {
        return deliveredMemo;
    }
}

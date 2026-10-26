package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 소모임 일정 — V92 {@code group_events}(community api-groups.md 9절). 만들기·수정·취소는 소모임 행을 잠근 트랜잭션 안에서 하고, 읽을 때는 {@link #state} 가 지금 시각으로 상태를 정한다.
 * 끝나는 시각({@code closesAt})은 {@code endsAt} 이 있으면 그 값, 없으면 시작 + {@link #DEFAULT_LENGTH} — 저장할 때 계산해 두어 질의가 한 조건으로 「끝났나」를 본다.
 */
@Entity
@Table(name = "group_events")
@DynamicUpdate
public class GroupEventEntity {

    /** 끝나는 시각을 정하지 않은 일정이 「끝난 것」으로 치는 시작 후 시간 */
    public static final Duration DEFAULT_LENGTH = Duration.ofHours(2);

    /** 일정의 지금 상태 — 서버가 시각으로 정한다(저장하지 않는다) */
    public enum State {
        UPCOMING, ONGOING, ENDED, CANCELED
    }

    @Id
    private Long id;

    private Long groupId;
    private Long creatorId;
    private String title;
    private String description;
    private Instant startsAt;
    private Instant endsAt;
    private Instant closesAt;
    @Enumerated(EnumType.STRING)
    private GroupEventMode mode;
    private String place;
    @Enumerated(EnumType.STRING)
    private GroupEventStatus status;
    private String cancelReason;
    private Instant canceledAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected GroupEventEntity() {
    }

    public static GroupEventEntity create(long id, long groupId, long creatorId, String title, String description, Instant startsAt, Instant endsAt,
                                          GroupEventMode mode, String place, Instant now) {
        GroupEventEntity e = new GroupEventEntity();
        e.id = id;
        e.groupId = groupId;
        e.creatorId = creatorId;
        e.status = GroupEventStatus.ACTIVE;
        e.createdAt = now;
        e.apply(title, description, startsAt, endsAt, mode, place, now);
        return e;
    }

    /** 고친 내용을 채운다 — 온라인이면 장소를 비운다. 끝나는 시각도 다시 계산한다 */
    public void apply(String title, String description, Instant startsAt, Instant endsAt, GroupEventMode mode, String place, Instant now) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.closesAt = endsAt != null ? endsAt : startsAt.plus(DEFAULT_LENGTH);
        this.mode = mode;
        this.place = mode == GroupEventMode.OFFLINE ? place : null;
        this.updatedAt = now;
    }

    public void cancel(String reason, Instant now) {
        this.status = GroupEventStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    /** 지금 시각({@code now})의 상태: 취소 → 끝남 → 진행 중 → 시작 전 */
    public State state(Instant now) {
        if (status == GroupEventStatus.CANCELED) {
            return State.CANCELED;
        }
        if (!now.isBefore(closesAt)) {
            return State.ENDED;
        }
        return now.isBefore(startsAt) ? State.UPCOMING : State.ONGOING;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public GroupEventMode getMode() {
        return mode;
    }

    public String getPlace() {
        return place;
    }

    public GroupEventStatus getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

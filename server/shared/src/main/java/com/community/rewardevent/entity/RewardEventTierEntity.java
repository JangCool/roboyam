package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 등수(보상 묶음) — shared V163 {@code reward_event_tiers}. 넣기는 저장소({@code persist} + 내보내기), 이름·인원·차례 고치기와 지우기는 저장소의 DML */
@Entity
@Table(name = "reward_event_tiers")
public class RewardEventTierEntity {

    @Id
    private Long id;
    private Long eventId;
    private short position;
    private String name;
    private int quota;

    protected RewardEventTierEntity() {
    }

    public static RewardEventTierEntity create(long id, long eventId, int position, String name, int quota) {
        RewardEventTierEntity t = new RewardEventTierEntity();
        t.id = id;
        t.eventId = eventId;
        t.position = (short) position;
        t.name = name;
        t.quota = quota;
        return t;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public short getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public int getQuota() {
        return quota;
    }
}

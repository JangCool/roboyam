package com.community.rewardevent.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 댓글 추첨 기록 — shared V163 {@code reward_event_draws}. 추첨 번호(seed)와 후보 목록(회원 번호 오름차순)이 같으면 같은 결과다. 넣기만 한다 */
@Entity
@Table(name = "reward_event_draws")
public class RewardEventDrawEntity {

    @Id
    private Long id;
    private Long eventId;
    private Long tierId;
    private Long operatorId;
    private Long postId;
    private int candidateCount;
    private int picked;
    private long seed;
    private Long[] pickedUserIds;
    private Instant createdAt;

    protected RewardEventDrawEntity() {
    }

    public static RewardEventDrawEntity create(long id, long eventId, long tierId, long operatorId, long postId, int candidateCount, long seed, Long[] picked, Instant now) {
        RewardEventDrawEntity d = new RewardEventDrawEntity();
        d.id = id;
        d.eventId = eventId;
        d.tierId = tierId;
        d.operatorId = operatorId;
        d.postId = postId;
        d.candidateCount = candidateCount;
        d.picked = picked.length;
        d.seed = seed;
        d.pickedUserIds = picked.clone();
        d.createdAt = now;
        return d;
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

    public Long getOperatorId() {
        return operatorId;
    }

    public Long getPostId() {
        return postId;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public int getPicked() {
        return picked;
    }

    public long getSeed() {
        return seed;
    }

    public Long[] getPickedUserIds() {
        return pickedUserIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

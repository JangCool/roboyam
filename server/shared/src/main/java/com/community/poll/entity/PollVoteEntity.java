package com.community.poll.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 고른 선택지 한 줄 — V81 {@code poll_votes}(복수 선택이면 한 사람이 여러 줄). <b>익명</b>: 사람별 목록을 내는 질의를 두지 않는다(본인 것만 읽는다).
 * 다시 투표 = 그 사람의 줄을 지우고 새로 넣는다.
 */
@Entity
@Table(name = "poll_votes")
@IdClass(PollVoteId.class)
public class PollVoteEntity {

    @Id
    private Long pollId;

    @Id
    private Long userId;

    @Id
    private Long optionId;

    private Instant createdAt;

    protected PollVoteEntity() {
    }

    public static PollVoteEntity create(long pollId, long userId, long optionId, Instant now) {
        PollVoteEntity vote = new PollVoteEntity();
        vote.pollId = pollId;
        vote.userId = userId;
        vote.optionId = optionId;
        vote.createdAt = now;
        return vote;
    }
}

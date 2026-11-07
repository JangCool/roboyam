package com.community.poll.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link PollVoteEntity} 의 복합 키(투표 + 회원 + 선택지) */
public class PollVoteId implements Serializable {

    private Long pollId;
    private Long userId;
    private Long optionId;

    protected PollVoteId() {
    }

    public PollVoteId(Long pollId, Long userId, Long optionId) {
        this.pollId = pollId;
        this.userId = userId;
        this.optionId = optionId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PollVoteId that && Objects.equals(pollId, that.pollId) && Objects.equals(userId, that.userId) && Objects.equals(optionId, that.optionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pollId, userId, optionId);
    }
}

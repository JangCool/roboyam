package com.community.point.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** {@link PointPopularRewardEntity} 의 키 — (날짜, 순위) */
public class PointPopularRewardId implements Serializable {

    private LocalDate rewardDay;
    private int rank;

    protected PointPopularRewardId() {
    }

    public PointPopularRewardId(LocalDate rewardDay, int rank) {
        this.rewardDay = rewardDay;
        this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PointPopularRewardId other && Objects.equals(rewardDay, other.rewardDay) && rank == other.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rewardDay, rank);
    }
}

package com.community.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 인기글 보상 대상 한 줄 — {@code point_popular_rewards}(V238, point-board-tier-contract 「인기글 보상」). 날짜(한국 시간)마다 뽑은 순위 그대로 남긴다 — 다시 돌려도 다시 뽑지 않고
 * 남긴 목록으로 지급만 잇는다(지급은 원장 {@code POPULAR}, 대상 글 — 원장 유일). 넣기는 DB 함수({@code popular_rank_candidates})를 쓰는 한 문장({@code PointPopularRewardStore#pick}).
 */
@Entity
@Table(name = "point_popular_rewards")
@IdClass(PointPopularRewardId.class)
public class PointPopularRewardEntity {

    /** 보상 날짜 — {@code day} 는 HQL 함수 이름과 겹쳐 속성 이름을 달리 둔다 */
    @Id
    @Column(name = "day")
    private LocalDate rewardDay;
    @Id
    private int rank;

    private Long postId;
    private Long authorId;
    private double score;
    private Instant createdAt;

    protected PointPopularRewardEntity() {
    }

    public LocalDate getRewardDay() {
        return rewardDay;
    }

    public int getRank() {
        return rank;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public double getScore() {
        return score;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

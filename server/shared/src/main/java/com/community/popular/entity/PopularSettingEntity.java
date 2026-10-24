package com.community.popular.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 인기글 설정(행 하나, {@code id = 1}) — V74__popular_settings_exclusions.sql 의 {@code popular_settings}. 행은 마이그레이션이 만든다.
 * DB 배치 {@code popular-rank}(R__batch_24)와 공개 인기글 읽기가 이 행을 읽고, 관리자 「인기글 설정」이 {@link #change} 로만 바꾼다.
 */
@Entity
@Table(name = "popular_settings")
public class PopularSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private BigDecimal likeWeight;
    private BigDecimal commentWeight;
    private BigDecimal viewWeight;
    private BigDecimal gravity;
    private BigDecimal ageOffsetHours;
    private int minLikes;
    private int minComments;
    private int minViews;
    private boolean excludeStaffBoards;
    private int maxPerAuthor;
    private String weekStart;
    private Instant updatedAt;
    private Long updatedBy;

    protected PopularSettingEntity() {
    }

    /** 통째로 바꾼다(검사는 부르는 쪽이 했다) */
    public void change(BigDecimal likeWeight, BigDecimal commentWeight, BigDecimal viewWeight, BigDecimal gravity, BigDecimal ageOffsetHours, int minLikes,
                       int minComments, int minViews, boolean excludeStaffBoards, int maxPerAuthor, String weekStart, long operatorId, Instant now) {
        this.likeWeight = likeWeight;
        this.commentWeight = commentWeight;
        this.viewWeight = viewWeight;
        this.gravity = gravity;
        this.ageOffsetHours = ageOffsetHours;
        this.minLikes = minLikes;
        this.minComments = minComments;
        this.minViews = minViews;
        this.excludeStaffBoards = excludeStaffBoards;
        this.maxPerAuthor = maxPerAuthor;
        this.weekStart = weekStart;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public BigDecimal getLikeWeight() {
        return likeWeight;
    }

    public BigDecimal getCommentWeight() {
        return commentWeight;
    }

    public BigDecimal getViewWeight() {
        return viewWeight;
    }

    public BigDecimal getGravity() {
        return gravity;
    }

    public BigDecimal getAgeOffsetHours() {
        return ageOffsetHours;
    }

    public int getMinLikes() {
        return minLikes;
    }

    public int getMinComments() {
        return minComments;
    }

    public int getMinViews() {
        return minViews;
    }

    public boolean isExcludeStaffBoards() {
        return excludeStaffBoards;
    }

    public int getMaxPerAuthor() {
        return maxPerAuthor;
    }

    public String getWeekStart() {
        return weekStart;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

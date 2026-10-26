package com.community.point.entity;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 포인트 정책(행 하나) — V51__points.sql 의 {@code point_settings}. 값은 {@link PointRules} 로 읽고 {@link #change} 로만 바꾼다 */
@Entity
@Table(name = "point_settings")
public class PointSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private int attendancePoints;
    private int postPoints;
    private int postDailyCap;
    private int commentPoints;
    private int commentDailyCap;
    private int popularRewardPoints;
    @Column(name = "popular_reward_top_n")
    private int popularRewardTopN;
    private int likeBonusThreshold;
    private int likeBonusPoints;
    private int likeBonusDailyLimit;
    private int dealEndRequestPoints;
    private int dealEndRequestDailyLimit;
    private int signupPoints;
    // ── V85 묶음 정책 — 관리자 정책 화면 14칸 밖이라 이 매핑으로는 바꾸지 않는다(읽기만, 화면 칸은 1-3) ──
    @Column(insertable = false, updatable = false)
    private int freeValidDays;
    @Column(insertable = false, updatable = false)
    private int restoredLotValidDays;
    @Column(insertable = false, updatable = false)
    private int expiryNoticeDays;
    @Column(insertable = false, updatable = false)
    private int expiryNoticeIntervalDays;
    @Column(insertable = false, updatable = false)
    private Instant lotTrackingStartedAt;
    @Column(insertable = false, updatable = false)
    private int adjustLargeThreshold;
    private Instant updatedAt;
    private Long updatedBy;

    protected PointSettingEntity() {
    }

    public PointRules values() {
        return new PointRules(attendancePoints, postPoints, postDailyCap, commentPoints, commentDailyCap, popularRewardPoints, popularRewardTopN, likeBonusThreshold,
                likeBonusPoints, likeBonusDailyLimit, dealEndRequestPoints, dealEndRequestDailyLimit, signupPoints);
    }

    public void change(PointRules v, long operatorId, Instant now) {
        this.attendancePoints = v.attendancePoints();
        this.postPoints = v.postPoints();
        this.postDailyCap = v.postDailyCap();
        this.commentPoints = v.commentPoints();
        this.commentDailyCap = v.commentDailyCap();
        this.popularRewardPoints = v.popularRewardPoints();
        this.popularRewardTopN = v.popularRewardTopN();
        this.likeBonusThreshold = v.likeBonusThreshold();
        this.likeBonusPoints = v.likeBonusPoints();
        this.likeBonusDailyLimit = v.likeBonusDailyLimit();
        this.dealEndRequestPoints = v.dealEndRequestPoints();
        this.dealEndRequestDailyLimit = v.dealEndRequestDailyLimit();
        this.signupPoints = v.signupPoints();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 묶음 정책(V85) */
    public PointLotRules lotRules() {
        return new PointLotRules(freeValidDays, restoredLotValidDays, expiryNoticeDays, expiryNoticeIntervalDays, lotTrackingStartedAt, adjustLargeThreshold);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

package com.community.point.entity;

import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.dto.PointTierRules;
import com.community.point.dto.PointTierValues;
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
    // ── V90 적립 조건(커머스 1-3 — commerce.md 6-2) ──
    private int postMinLength;
    private int commentMinLength;
    private boolean commentOwnPostExcluded;
    private boolean commentOncePerPost;
    private int writeIntervalSeconds;
    private int dailyTotalCap;
    private int attendanceStreakDays;
    private int attendanceStreakBonus;
    // ── V85 묶음 정책 — 정책 화면 칸이 아니라 이 매핑으로는 바꾸지 않는다(읽기만). 큰 금액 조정 문턱만 1-3 부터 정책 화면에서 바꾼다(changeAdjustLargeThreshold) ──
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
    @Column(insertable = false)
    private int adjustLargeThreshold;
    // ── V238 게시판 등급별 지급(point-board-tier-contract) — 일반 등급의 쓰면 바로·최소 글자 수는 위 postPoints·postMinLength ──
    private int tierNormalLikeBonusPoints;
    private int tierNormalFeaturedPoints;
    private int tierNormalMinImages;
    private int tierNormalHoldHours;
    private int tierInfoPostPoints;
    private int tierInfoLikeBonusPoints;
    private int tierInfoFeaturedPoints;
    private int tierInfoMinLength;
    private int tierInfoMinImages;
    private int tierInfoHoldHours;
    private int tierCorePostPoints;
    private int tierCoreLikeBonusPoints;
    private int tierCoreFeaturedPoints;
    private int tierCoreMinLength;
    private int tierCoreMinImages;
    private int tierCoreHoldHours;
    private boolean infoSourceOnce;
    private boolean excludeAiAndOps;
    private Instant updatedAt;
    private Long updatedBy;

    protected PointSettingEntity() {
    }

    public PointRules values() {
        return new PointRules(attendancePoints, postPoints, postDailyCap, commentPoints, commentDailyCap, popularRewardPoints, popularRewardTopN, likeBonusThreshold,
                likeBonusPoints, likeBonusDailyLimit, dealEndRequestPoints, dealEndRequestDailyLimit, signupPoints, postMinLength, commentMinLength,
                commentOwnPostExcluded, commentOncePerPost, writeIntervalSeconds, dailyTotalCap, attendanceStreakDays, attendanceStreakBonus);
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
        this.postMinLength = v.postMinLength();
        this.commentMinLength = v.commentMinLength();
        this.commentOwnPostExcluded = v.commentOwnPostExcluded();
        this.commentOncePerPost = v.commentOncePerPost();
        this.writeIntervalSeconds = v.writeIntervalSeconds();
        this.dailyTotalCap = v.dailyTotalCap();
        this.attendanceStreakDays = v.attendanceStreakDays();
        this.attendanceStreakBonus = v.attendanceStreakBonus();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 게시판 등급 칸(V238) — 일반 등급의 쓰면 바로·최소 글자 수 자리는 0(전역 칸을 따른다) */
    public PointTierRules tierRules() {
        return new PointTierRules(new PointTierValues(0, tierNormalLikeBonusPoints, tierNormalFeaturedPoints, 0, tierNormalMinImages, tierNormalHoldHours),
                new PointTierValues(tierInfoPostPoints, tierInfoLikeBonusPoints, tierInfoFeaturedPoints, tierInfoMinLength, tierInfoMinImages, tierInfoHoldHours),
                new PointTierValues(tierCorePostPoints, tierCoreLikeBonusPoints, tierCoreFeaturedPoints, tierCoreMinLength, tierCoreMinImages, tierCoreHoldHours),
                infoSourceOnce, excludeAiAndOps);
    }

    /** 게시판 등급 칸을 바꾼다(범위 검사는 부르는 쪽) — 일반 등급의 쓰면 바로·최소 글자 수는 받지 않는다(전역 칸) */
    public void changeTiers(PointTierRules t, long operatorId, Instant now) {
        this.tierNormalLikeBonusPoints = t.normal().likeBonusPoints();
        this.tierNormalFeaturedPoints = t.normal().featuredPoints();
        this.tierNormalMinImages = t.normal().minImages();
        this.tierNormalHoldHours = t.normal().holdHours();
        this.tierInfoPostPoints = t.info().postPoints();
        this.tierInfoLikeBonusPoints = t.info().likeBonusPoints();
        this.tierInfoFeaturedPoints = t.info().featuredPoints();
        this.tierInfoMinLength = t.info().minLength();
        this.tierInfoMinImages = t.info().minImages();
        this.tierInfoHoldHours = t.info().holdHours();
        this.tierCorePostPoints = t.core().postPoints();
        this.tierCoreLikeBonusPoints = t.core().likeBonusPoints();
        this.tierCoreFeaturedPoints = t.core().featuredPoints();
        this.tierCoreMinLength = t.core().minLength();
        this.tierCoreMinImages = t.core().minImages();
        this.tierCoreHoldHours = t.core().holdHours();
        this.infoSourceOnce = t.infoSourceOnce();
        this.excludeAiAndOps = t.excludeAiAndOps();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 큰 금액 조정 문턱(V85 칸 — 1-3 부터 정책 화면에서 저장, 0~100,000. 범위 검사는 부르는 쪽) */
    public void changeAdjustLargeThreshold(int threshold, long operatorId, Instant now) {
        this.adjustLargeThreshold = threshold;
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

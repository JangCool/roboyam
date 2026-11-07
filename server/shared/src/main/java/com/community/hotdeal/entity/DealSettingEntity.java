package com.community.hotdeal.entity;

import com.community.hotdeal.dto.DealSettings;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 핫딜 설정 행 — V122 {@code deal_settings}(id = 1, 마이그레이션이 기본값으로 만든다). 범위는 DB CHECK 가 막는다. 공개·관리자 서버는 짧은 사본({@code DealSettingsReader})으로 읽고,
 * 관리자 핫딜 설정 화면이 잠금 조회 뒤 {@link #apply} 로 바꾼다(변경 감지).
 */
@Entity
@Table(name = "deal_settings")
public class DealSettingEntity {

    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private int endRequestThreshold;
    private int endGraceHours;
    private int endRequestDailyLimit;
    private int endRequestPerAuthorLimit;
    private int endRequestPerAuthorDays;
    private int keepMaxCount;
    private int memberDailyPostLimit;
    private int sameStoreIntervalMinutes;
    private int autoExpireDays;
    private long priceMaxWon;
    private int discountWarnPercent;
    private int hotLikeThreshold;
    private int suspicionMinPosts;
    private int suspicionWindowDays;
    private int storeSuspicionMembers;
    private int storeSuspicionWindowDays;
    private int storeSuspicionOtherRatioPercent;
    private boolean ogFetchEnabled;
    /** MEMBER · SITE */
    private String affiliateMode;
    private Instant updatedAt;
    /** 마지막으로 저장한 운영자(행이 만들어진 뒤 아무도 저장하지 않았으면 null) */
    private Long updatedBy;

    protected DealSettingEntity() {
    }

    public DealSettings settings() {
        return new DealSettings(endRequestThreshold, endGraceHours, endRequestDailyLimit, endRequestPerAuthorLimit, endRequestPerAuthorDays, keepMaxCount,
                memberDailyPostLimit, sameStoreIntervalMinutes, autoExpireDays, priceMaxWon, discountWarnPercent, hotLikeThreshold, suspicionMinPosts, suspicionWindowDays,
                storeSuspicionMembers, storeSuspicionWindowDays, storeSuspicionOtherRatioPercent, ogFetchEnabled, affiliateMode);
    }

    public void apply(DealSettings s, long operatorId, Instant now) {
        this.endRequestThreshold = s.endRequestThreshold();
        this.endGraceHours = s.endGraceHours();
        this.endRequestDailyLimit = s.endRequestDailyLimit();
        this.endRequestPerAuthorLimit = s.endRequestPerAuthorLimit();
        this.endRequestPerAuthorDays = s.endRequestPerAuthorDays();
        this.keepMaxCount = s.keepMaxCount();
        this.memberDailyPostLimit = s.memberDailyPostLimit();
        this.sameStoreIntervalMinutes = s.sameStoreIntervalMinutes();
        this.autoExpireDays = s.autoExpireDays();
        this.priceMaxWon = s.priceMaxWon();
        this.discountWarnPercent = s.discountWarnPercent();
        this.hotLikeThreshold = s.hotLikeThreshold();
        this.suspicionMinPosts = s.suspicionMinPosts();
        this.suspicionWindowDays = s.suspicionWindowDays();
        this.storeSuspicionMembers = s.storeSuspicionMembers();
        this.storeSuspicionWindowDays = s.storeSuspicionWindowDays();
        this.storeSuspicionOtherRatioPercent = s.storeSuspicionOtherRatioPercent();
        this.ogFetchEnabled = s.ogFetchEnabled();
        this.affiliateMode = s.affiliateMode();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

package com.community.group.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 소모임 설정 네 구획 — V90 {@code group_settings}(단일 행 id = 1, 마이그레이션이 넣어 둔다). 칸이 null 이면 그 칸은 서버 설정 파일 값이고,
 * 구획의 {@code *UpdatedAt} 이 null 이면 그 구획은 저장된 적이 없다(fromDefaults). 읽는 곳은 {@code GroupSettingsService} 한 곳 — 캐시하지 않는다.
 */
@Entity
@Table(name = "group_settings")
@DynamicUpdate
public class GroupSettingsEntity {

    public static final short ROW_ID = 1;

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    private GroupCreationMode creationMode;
    private String minGrade;
    private Short maxPerOwner;
    private Long creationUpdatedBy;
    private Instant creationUpdatedAt;

    private Boolean managersEnabled;
    private Short managerLimitDefault;
    private Short managerLimitMax;
    private Long managerUpdatedBy;
    private Instant managerUpdatedAt;

    private Boolean visibilityEnabled;
    private Short visibilityCooldownDays;
    private Short visibilityRetryHours;
    private Short visibilityRejectLimit;
    private Short visibilityDailyLimit;
    private Short visibilityTextMax;
    private Instant visibilityNoticeSentAt;
    private Long visibilityUpdatedBy;
    private Instant visibilityUpdatedAt;

    private Short reapplyDays;
    private Short capacityMin;
    private Short capacityMax;
    private Short trendingSize;
    private Short trendingActiveDays;
    private Short eventMaxUpcoming;
    private Short eventMaxDaysAhead;
    private Short eventMaxLengthDays;
    private Long policyUpdatedBy;
    private Instant policyUpdatedAt;

    protected GroupSettingsEntity() {
    }

    public void saveCreation(GroupCreationMode mode, String minGrade, int maxPerOwner, long operatorId, Instant now) {
        this.creationMode = mode;
        this.minGrade = minGrade;
        this.maxPerOwner = (short) maxPerOwner;
        this.creationUpdatedBy = operatorId;
        this.creationUpdatedAt = now;
    }

    public void saveManager(boolean enabled, int limitDefault, int limitMax, long operatorId, Instant now) {
        this.managersEnabled = enabled;
        this.managerLimitDefault = (short) limitDefault;
        this.managerLimitMax = (short) limitMax;
        this.managerUpdatedBy = operatorId;
        this.managerUpdatedAt = now;
    }

    public void saveVisibility(boolean enabled, int cooldownDays, int retryHours, int rejectLimit, int dailyLimit, int textMax, long operatorId, Instant now) {
        this.visibilityEnabled = enabled;
        this.visibilityCooldownDays = (short) cooldownDays;
        this.visibilityRetryHours = (short) retryHours;
        this.visibilityRejectLimit = (short) rejectLimit;
        this.visibilityDailyLimit = (short) dailyLimit;
        this.visibilityTextMax = (short) textMax;
        this.visibilityUpdatedBy = operatorId;
        this.visibilityUpdatedAt = now;
    }

    public void savePolicy(int reapplyDays, int capacityMin, int capacityMax, int trendingSize, int trendingActiveDays, int eventMaxUpcoming, int eventMaxDaysAhead,
                           int eventMaxLengthDays, long operatorId, Instant now) {
        this.reapplyDays = (short) reapplyDays;
        this.capacityMin = (short) capacityMin;
        this.capacityMax = (short) capacityMax;
        this.trendingSize = (short) trendingSize;
        this.trendingActiveDays = (short) trendingActiveDays;
        this.eventMaxUpcoming = (short) eventMaxUpcoming;
        this.eventMaxDaysAhead = (short) eventMaxDaysAhead;
        this.eventMaxLengthDays = (short) eventMaxLengthDays;
        this.policyUpdatedBy = operatorId;
        this.policyUpdatedAt = now;
    }

    public GroupCreationMode getCreationMode() {
        return creationMode;
    }

    public String getMinGrade() {
        return minGrade;
    }

    public Short getMaxPerOwner() {
        return maxPerOwner;
    }

    public Long getCreationUpdatedBy() {
        return creationUpdatedBy;
    }

    public Instant getCreationUpdatedAt() {
        return creationUpdatedAt;
    }

    public Boolean getManagersEnabled() {
        return managersEnabled;
    }

    public Short getManagerLimitDefault() {
        return managerLimitDefault;
    }

    public Short getManagerLimitMax() {
        return managerLimitMax;
    }

    public Long getManagerUpdatedBy() {
        return managerUpdatedBy;
    }

    public Instant getManagerUpdatedAt() {
        return managerUpdatedAt;
    }

    public Boolean getVisibilityEnabled() {
        return visibilityEnabled;
    }

    public Short getVisibilityCooldownDays() {
        return visibilityCooldownDays;
    }

    public Short getVisibilityRetryHours() {
        return visibilityRetryHours;
    }

    public Short getVisibilityRejectLimit() {
        return visibilityRejectLimit;
    }

    public Short getVisibilityDailyLimit() {
        return visibilityDailyLimit;
    }

    public Short getVisibilityTextMax() {
        return visibilityTextMax;
    }

    public Instant getVisibilityNoticeSentAt() {
        return visibilityNoticeSentAt;
    }

    public Long getVisibilityUpdatedBy() {
        return visibilityUpdatedBy;
    }

    public Instant getVisibilityUpdatedAt() {
        return visibilityUpdatedAt;
    }

    public Short getReapplyDays() {
        return reapplyDays;
    }

    public Short getCapacityMin() {
        return capacityMin;
    }

    public Short getCapacityMax() {
        return capacityMax;
    }

    public Short getTrendingSize() {
        return trendingSize;
    }

    public Short getTrendingActiveDays() {
        return trendingActiveDays;
    }

    public Short getEventMaxUpcoming() {
        return eventMaxUpcoming;
    }

    public Short getEventMaxDaysAhead() {
        return eventMaxDaysAhead;
    }

    public Short getEventMaxLengthDays() {
        return eventMaxLengthDays;
    }

    public Long getPolicyUpdatedBy() {
        return policyUpdatedBy;
    }

    public Instant getPolicyUpdatedAt() {
        return policyUpdatedAt;
    }
}

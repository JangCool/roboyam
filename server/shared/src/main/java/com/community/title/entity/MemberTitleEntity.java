package com.community.title.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 회원이 받은 칭호 한 줄 — {@code member_titles}(V240). 같은 칭호·같은 기간({@code period} — 이달의 작가 {@code YYYY-MM}, 올해의 작가 {@code YYYY}, 그 밖 빈 값)의 살아 있는 줄은 하나
 * ({@code ux_member_titles_active}). 단계가 오르면 같은 줄을 고치고 축하 창을 다시 띄운다({@link #raiseTier}). 회수해도 줄은 남긴다({@link #revoke}).
 */
@Entity
@Table(name = "member_titles")
public class MemberTitleEntity {

    @Id
    private Long id;
    private Long userId;
    private Long titleId;
    @Enumerated(EnumType.STRING)
    private TitleTier tier;
    private String period;
    @Enumerated(EnumType.STRING)
    private TitleSource source;
    private Long applicationId;
    private Instant grantedAt;
    private Long grantedBy;
    private Long grantedByMember;
    private String grantReason;
    private Instant tierChangedAt;
    private Instant seenAt;
    private Instant revokedAt;
    private Long revokedBy;
    private String revokedReason;

    protected MemberTitleEntity() {
    }

    /**
     * @param grantedBy       준 운영자(자동·매니저 승인은 null)
     * @param grantedByMember 승인한 게시판 매니저(그 밖 null)
     */
    public static MemberTitleEntity grant(long id, long userId, long titleId, TitleTier tier, String period, TitleSource source, Long applicationId, Long grantedBy,
                                          Long grantedByMember, String reason, Instant now) {
        MemberTitleEntity e = new MemberTitleEntity();
        e.id = id;
        e.userId = userId;
        e.titleId = titleId;
        e.tier = tier;
        e.period = period == null ? "" : period;
        e.source = source;
        e.applicationId = applicationId;
        e.grantedAt = now;
        e.grantedBy = grantedBy;
        e.grantedByMember = grantedByMember;
        e.grantReason = reason;
        return e;
    }

    /** 단계를 올린다 — 축하 창을 다시 띄운다(낮추지 않는다: 부르는 쪽이 더 높을 때만 부른다) */
    public void raiseTier(TitleTier next, Instant now) {
        this.tier = next;
        this.tierChangedAt = now;
        this.seenAt = null;
    }

    /** 단계를 낮춘다(체험단 약속 지킴 판정을 되돌림 — 점검 L4). 알림·축하 창 없이 — {@code null} 이면 단계 전 */
    public void lowerTier(TitleTier next, Instant now) {
        this.tier = next;
        this.tierChangedAt = now;
    }

    /** 축하 창을 닫았다 */
    public void markSeen(Instant now) {
        if (seenAt == null) {
            this.seenAt = now;
        }
    }

    /** 회수 — 줄은 남기고 회수한 운영자·시각·사유를 적는다 */
    public void revoke(Long operatorId, String reason, Instant now) {
        this.revokedAt = now;
        this.revokedBy = operatorId;
        this.revokedReason = reason;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTitleId() {
        return titleId;
    }

    public TitleTier getTier() {
        return tier;
    }

    public String getPeriod() {
        return period;
    }

    public TitleSource getSource() {
        return source;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Long getGrantedBy() {
        return grantedBy;
    }

    public Long getGrantedByMember() {
        return grantedByMember;
    }

    public String getGrantReason() {
        return grantReason;
    }

    public Instant getTierChangedAt() {
        return tierChangedAt;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Long getRevokedBy() {
        return revokedBy;
    }

    public String getRevokedReason() {
        return revokedReason;
    }
}

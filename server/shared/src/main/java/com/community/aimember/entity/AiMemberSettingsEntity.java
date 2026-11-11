package com.community.aimember.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** AI 회원 설정 — {@code ai_member_settings} 행 하나(id = 1, shared V183). 전체 정지 · 바로 게시 · 하루 호출 상한 · 회원 글 댓글 허용(기본 꺼짐) · 글 쓰는 때(V191) */
@Entity
@Table(name = "ai_member_settings")
public class AiMemberSettingsEntity {

    public static final int ID = 1;

    @Id
    private Integer id;

    private boolean stopped;
    private Instant stoppedAt;
    private Long stoppedBy;
    private boolean instantPublish;

    @Enumerated(EnumType.STRING)
    private AiInstantScope instantScope;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] instantAccountIds;

    private int dailyCallLimit;
    private boolean commentOnMemberPosts;
    /** 글 쓰는 때(V191) — 조용한 게시판 기준 일수 · 글이 잘 올라오는 게시판의 AI 글 간격(일) · 계정당 하루 최대 새 글 */
    private short quietDays;
    private short busyIntervalDays;
    private short dailyPostMax;
    private Instant updatedAt;
    private Long updatedBy;

    protected AiMemberSettingsEntity() {
    }

    /** 글 쓰는 때 값(검사를 마친 것) */
    public record PostTiming(int quietDays, int busyIntervalDays, int dailyPostMax) {
    }

    /** 전체 정지 켜기·끄기(바로 반영) */
    public void stop(boolean on, long operatorId, Instant now) {
        this.stopped = on;
        this.stoppedAt = on ? now : null;
        this.stoppedBy = on ? operatorId : null;
    }

    /** 설정 저장 — 바로 게시가 꺼져 있거나 범위가 전체면 고른 계정은 비운다 */
    public void save(boolean instantPublish, AiInstantScope scope, List<Long> accountIds, int dailyCallLimit, boolean commentOnMemberPosts, PostTiming timing,
                     long operatorId, Instant now) {
        this.instantPublish = instantPublish;
        this.instantScope = scope;
        this.instantAccountIds = instantPublish && scope == AiInstantScope.SELECTED ? accountIds.toArray(Long[]::new) : new Long[0];
        this.dailyCallLimit = dailyCallLimit;
        this.commentOnMemberPosts = commentOnMemberPosts;
        this.quietDays = (short) timing.quietDays();
        this.busyIntervalDays = (short) timing.busyIntervalDays();
        this.dailyPostMax = (short) timing.dailyPostMax();
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    /** 그 AI 계정이 바로 게시 대상인가(정책 6) */
    public boolean instantFor(long aiMemberId) {
        if (!instantPublish) {
            return false;
        }
        return instantScope != AiInstantScope.SELECTED || getInstantAccountIds().contains(aiMemberId);
    }

    public boolean isStopped() {
        return stopped;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public Long getStoppedBy() {
        return stoppedBy;
    }

    public boolean isInstantPublish() {
        return instantPublish;
    }

    public AiInstantScope getInstantScope() {
        return instantScope == null ? AiInstantScope.ALL : instantScope;
    }

    public List<Long> getInstantAccountIds() {
        return instantAccountIds == null ? List.of() : Arrays.asList(instantAccountIds.clone());
    }

    public int getDailyCallLimit() {
        return dailyCallLimit;
    }

    public boolean isCommentOnMemberPosts() {
        return commentOnMemberPosts;
    }

    public PostTiming getPostTiming() {
        return new PostTiming(quietDays, busyIntervalDays, dailyPostMax);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}

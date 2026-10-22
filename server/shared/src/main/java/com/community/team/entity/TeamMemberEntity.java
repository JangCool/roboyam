package com.community.team.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/** 팀원 한 사람 — V41__teams_notification_channels.sql 의 {@code team_members}. 운영자 계정이 지워지면 함께 지워진다(DB 연쇄 삭제) */
@Entity
@Table(name = "team_members")
@IdClass(TeamMemberId.class)
public class TeamMemberEntity {

    @Id
    private Long teamId;

    @Id
    private Long operatorId;

    private Instant createdAt;
    private Long createdBy;

    protected TeamMemberEntity() {
    }

    public static TeamMemberEntity create(long teamId, long operatorId, long createdBy, Instant now) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.teamId = teamId;
        member.operatorId = operatorId;
        member.createdAt = now;
        member.createdBy = createdBy;
        return member;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getOperatorId() {
        return operatorId;
    }
}

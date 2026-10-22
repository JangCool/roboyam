package com.community.team.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link TeamMemberEntity} 의 복합 키 — (팀, 운영자) */
public class TeamMemberId implements Serializable {

    private Long teamId;
    private Long operatorId;

    protected TeamMemberId() {
    }

    public TeamMemberId(Long teamId, Long operatorId) {
        this.teamId = teamId;
        this.operatorId = operatorId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TeamMemberId id && Objects.equals(teamId, id.teamId) && Objects.equals(operatorId, id.operatorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, operatorId);
    }
}

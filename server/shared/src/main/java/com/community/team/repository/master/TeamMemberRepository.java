package com.community.team.repository.master;

import com.community.team.entity.TeamMemberEntity;
import com.community.team.entity.TeamMemberId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Collection;
import java.util.List;

public interface TeamMemberRepository extends RoboyamRepository<TeamMemberEntity, TeamMemberId> {

    List<TeamMemberEntity> findByTeamId(Long teamId);

    long countByTeamId(Long teamId);

    void deleteByTeamIdAndOperatorIdIn(Long teamId, Collection<Long> operatorIds);
}

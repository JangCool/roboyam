package com.community.operator.repository.master;

import com.community.operator.entity.OperatorInvitationRoleEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Collection;
import java.util.List;

public interface OperatorInvitationRoleRepository extends RoboyamRepository<OperatorInvitationRoleEntity, Long> {

    List<OperatorInvitationRoleEntity> findByInvitationIdIn(Collection<Long> invitationIds);
}

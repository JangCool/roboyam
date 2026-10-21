package com.community.operator.repository.master;

import com.community.operator.entity.OperatorInvitationEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface OperatorInvitationRepository extends RoboyamRepository<OperatorInvitationEntity, Long> {

    /** 아직 수락하지 않은 초대 — 같은 이메일에는 하나뿐이다(유일 인덱스) */
    Optional<OperatorInvitationEntity> findByEmailAndConsumedAtIsNull(String email);

    /** 수락하지 않은 초대 전부(기한이 지난 것 포함), 최근 초대가 앞 */
    List<OperatorInvitationEntity> findByConsumedAtIsNullOrderByCreatedAtDescIdDesc();
}

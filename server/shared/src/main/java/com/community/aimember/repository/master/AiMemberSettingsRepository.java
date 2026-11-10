package com.community.aimember.repository.master;

import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** AI 회원 설정 행 하나(id = 1) */
public interface AiMemberSettingsRepository extends RoboyamRepository<AiMemberSettingsEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiMemberSettingsEntity> findWithLockById(int id);
}

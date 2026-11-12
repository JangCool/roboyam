package com.community.aimember.repository.master;

import com.community.aimember.entity.AiPromptSettingsEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** AI 회원 프롬프트 설정 행 하나(id = 1, shared V190) */
public interface AiPromptSettingsRepository extends IrobitRepository<AiPromptSettingsEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiPromptSettingsEntity> findWithLockById(int id);
}

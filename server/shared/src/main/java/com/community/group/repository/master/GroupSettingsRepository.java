package com.community.group.repository.master;

import com.community.group.entity.GroupSettingsEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface GroupSettingsRepository extends RoboyamRepository<GroupSettingsEntity, Short> {

    /** 구획 저장 — 행을 잠그고 읽는다(두 운영자가 같이 저장하면 줄을 선다 — 마지막 저장이 이긴다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GroupSettingsEntity> findWithLockById(Short id);
}

package com.community.popular.repository.master;

import com.community.popular.entity.PopularSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface PopularSettingRepository extends RoboyamRepository<PopularSettingEntity, Integer> {

    /** 설정 행을 쓰기 잠금으로 읽는다 — 저장을 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PopularSettingEntity> findWithLockById(Integer id);
}

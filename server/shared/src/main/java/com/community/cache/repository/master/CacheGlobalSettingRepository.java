package com.community.cache.repository.master;

import com.community.cache.entity.CacheGlobalSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 전체 캐시 스위치({@code cache_global_settings}, 1행) */
public interface CacheGlobalSettingRepository extends RoboyamRepository<CacheGlobalSettingEntity, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CacheGlobalSettingEntity> findWithLockById(Short id);
}

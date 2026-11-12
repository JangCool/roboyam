package com.community.point.repository.master;

import com.community.point.entity.PointSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface PointSettingRepository extends IrobitRepository<PointSettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PointSettingEntity> findWithLockById(Integer id);
}

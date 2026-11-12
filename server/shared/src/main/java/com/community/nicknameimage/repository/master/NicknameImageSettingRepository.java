package com.community.nicknameimage.repository.master;

import com.community.nicknameimage.entity.NicknameImageSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface NicknameImageSettingRepository extends IrobitRepository<NicknameImageSettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NicknameImageSettingEntity> findWithLockById(Integer id);
}

package com.community.address.repository.master;

import com.community.address.entity.AddressSearchSettingsEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface AddressSearchSettingsRepository extends RoboyamRepository<AddressSearchSettingsEntity, Short> {

    /** 저장할 때 — 줄을 잠근다(동시 저장이 전→후 감사를 어긋나게 하지 않게) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AddressSearchSettingsEntity> findWithLockById(short id);
}

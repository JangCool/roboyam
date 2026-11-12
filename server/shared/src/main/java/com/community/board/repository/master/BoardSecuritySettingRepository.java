package com.community.board.repository.master;

import com.community.board.entity.BoardSecuritySettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardSecuritySettingRepository extends IrobitRepository<BoardSecuritySettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardSecuritySettingEntity> findWithLockById(Integer id);
}

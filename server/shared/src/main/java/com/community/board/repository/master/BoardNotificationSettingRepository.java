package com.community.board.repository.master;

import com.community.board.entity.BoardNotificationSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardNotificationSettingRepository extends IrobitRepository<BoardNotificationSettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardNotificationSettingEntity> findWithLockById(Integer id);
}

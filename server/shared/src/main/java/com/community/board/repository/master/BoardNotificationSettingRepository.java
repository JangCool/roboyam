package com.community.board.repository.master;

import com.community.board.entity.BoardNotificationSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardNotificationSettingRepository extends RoboyamRepository<BoardNotificationSettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardNotificationSettingEntity> findWithLockById(Integer id);
}

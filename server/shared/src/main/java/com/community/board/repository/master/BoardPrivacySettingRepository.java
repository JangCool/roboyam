package com.community.board.repository.master;

import com.community.board.entity.BoardPrivacySettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardPrivacySettingRepository extends RoboyamRepository<BoardPrivacySettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardPrivacySettingEntity> findWithLockById(Integer id);
}

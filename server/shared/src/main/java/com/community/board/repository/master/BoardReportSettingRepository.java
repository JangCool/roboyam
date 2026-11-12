package com.community.board.repository.master;

import com.community.board.entity.BoardReportSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardReportSettingRepository extends IrobitRepository<BoardReportSettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardReportSettingEntity> findWithLockById(Integer id);
}

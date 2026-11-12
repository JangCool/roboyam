package com.community.board.repository.master;

import com.community.board.entity.BoardDefaultsEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardDefaultsRepository extends IrobitRepository<BoardDefaultsEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardDefaultsEntity> findWithLockById(Integer id);
}

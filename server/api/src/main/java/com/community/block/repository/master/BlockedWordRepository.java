package com.community.block.repository.master;

import com.community.block.entity.BlockedWordEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface BlockedWordRepository extends RoboyamRepository<BlockedWordEntity, Long> {

    /** 내 차단 단어 — 최근에 더한 것이 위(최대 50개라 쪽을 나누지 않는다) */
    List<BlockedWordEntity> findByUserIdOrderByIdDesc(Long userId);

    long countByUserId(Long userId);

    Optional<BlockedWordEntity> findByIdAndUserId(Long id, Long userId);
}

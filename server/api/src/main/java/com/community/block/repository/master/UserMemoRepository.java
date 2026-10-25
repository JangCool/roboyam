package com.community.block.repository.master;

import com.community.block.entity.UserMemoEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

public interface UserMemoRepository extends RoboyamRepository<UserMemoEntity, Long> {

    Optional<UserMemoEntity> findByUserIdAndTargetId(Long userId, Long targetId);

    /** 메모 수 상한 판정(처음 남길 때만) */
    long countByUserId(Long userId);
}

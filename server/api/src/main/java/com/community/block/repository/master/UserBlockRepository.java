package com.community.block.repository.master;

import com.community.block.entity.UserBlockEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

public interface UserBlockRepository extends RoboyamRepository<UserBlockEntity, Long> {

    Optional<UserBlockEntity> findByUserIdAndTargetId(Long userId, Long targetId);

    /** 차단 수 상한 판정 — 탈퇴한 회원의 줄도 센다(목록·total 에서만 뺀다) */
    long countByUserId(Long userId);
}

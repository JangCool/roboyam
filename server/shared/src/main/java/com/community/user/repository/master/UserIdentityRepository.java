package com.community.user.repository.master;

import com.community.user.entity.UserIdentityEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface UserIdentityRepository extends RoboyamRepository<UserIdentityEntity, Long> {

    /** 그 회원에게 연결된 소셜 계정(연결한 순서) */
    List<UserIdentityEntity> findByUserIdOrderByCreatedAtAsc(Long userId);
}

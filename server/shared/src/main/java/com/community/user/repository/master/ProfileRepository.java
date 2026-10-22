package com.community.user.repository.master;

import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 닉네임 중복 확인은 {@link ProfileRepositoryQDSL} 에 있다 */
public interface ProfileRepository extends RoboyamRepository<ProfileEntity, Long> {

    /** 행 잠금 — 즉시 승급이 같은 회원의 동시 승급(이력 두 줄)을 막는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProfileEntity> findWithLockByUserId(Long userId);
}

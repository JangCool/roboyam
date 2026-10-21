package com.community.role.repository.master;

import com.community.role.entity.RoleEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface RoleRepository extends RoboyamRepository<RoleEntity, String> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 역할을 동시에 바꾸거나 지우는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoleEntity> findWithLockByRoleId(String roleId);
}

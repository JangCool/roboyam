package com.community.user.repository.master;

import com.community.user.entity.UserEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends RoboyamRepository<UserEntity, Long> {

    /** 이메일은 소문자로 정규화한 값으로 찾는다 */
    boolean existsByEmail(String email);

    /** 이메일(소문자로 정규화한 값)로 찾는다. 탈퇴한 계정은 이메일이 지워져 있어 찾아지지 않는다 */
    Optional<UserEntity> findByEmail(String email);

    /** 행을 쓰기 잠금으로 읽는다 — 같은 회원의 요청을 줄 세워야 할 때(인증 메일 재발송의 60초 제한) 쓴다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findWithLockById(Long id);
}

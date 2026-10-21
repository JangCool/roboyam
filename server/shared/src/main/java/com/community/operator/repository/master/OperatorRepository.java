package com.community.operator.repository.master;

import com.community.operator.entity.OperatorEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface OperatorRepository extends RoboyamRepository<OperatorEntity, Long> {

    /** 이메일은 소문자로 정규화한 값으로 찾는다 */
    boolean existsByEmail(String email);

    Optional<OperatorEntity> findByEmail(String email);

    /** 그 이름(대소문자 무시)을 쓰는 <b>다른</b> 운영자가 있는가 — 내 정보에서 닉네임을 바꿀 때 */
    boolean existsByOperatorNameIgnoreCaseAndIdNot(String operatorName, Long id);

    /** 행을 쓰기 잠금으로 읽는다 — 같은 운영자를 동시에 바꾸는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperatorEntity> findWithLockById(Long id);
}

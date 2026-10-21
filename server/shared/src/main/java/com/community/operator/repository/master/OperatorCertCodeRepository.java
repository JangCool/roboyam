package com.community.operator.repository.master;

import com.community.operator.entity.OperatorCertCodeEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface OperatorCertCodeRepository extends RoboyamRepository<OperatorCertCodeEntity, Long> {

    /** 이메일 키의 승인번호 행을 쓰기 잠금으로 읽는다 — 같은 이메일의 동시 확인·재발급을 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OperatorCertCodeEntity> findWithLockByEmailKey(String emailKey);
}

package com.community.batch.repository.master;

import com.community.batch.entity.BatchJobEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BatchJobRepository extends RoboyamRepository<BatchJobEntity, String> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 배치의 설정을 동시에 바꾸는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BatchJobEntity> findWithLockByJobKey(String jobKey);
}

package com.community.grade.repository.master;

import com.community.grade.entity.GradeSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface GradeSettingRepository extends RoboyamRepository<GradeSettingEntity, Integer> {

    /** 행을 쓰기 잠금으로 읽는다 — 등급 저장을 한 번에 하나씩 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GradeSettingEntity> findWithLockById(Integer id);
}

package com.community.site.repository.master;

import com.community.site.entity.SiteSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface SiteSettingRepository extends RoboyamRepository<SiteSettingEntity, Integer> {

    /** 행을 쓰기 잠금으로 읽는다 — 이미지 교체·설정 저장을 한 번에 하나씩 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SiteSettingEntity> findWithLockById(Integer id);
}

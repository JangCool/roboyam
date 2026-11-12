package com.community.site.repository.master;

import com.community.site.entity.SiteOperatorEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface SiteOperatorRepository extends IrobitRepository<SiteOperatorEntity, Integer> {

    /** 행을 쓰기 잠금으로 읽는다 — 저장을 한 번에 하나씩 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SiteOperatorEntity> findWithLockById(Integer id);
}

package com.community.commerce.guard.repository.master;

import com.community.commerce.guard.entity.RequestRateLimitEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.Lock;

public interface RequestRateLimitRepository extends RoboyamRepository<RequestRateLimitEntity, String> {

    /** 저장할 때 — 모든 줄을 잠근다(같은 화면의 동시 저장이 전→후 감사를 어긋나게 하지 않게) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<RequestRateLimitEntity> findAllWithLockByActionIsNotNull();
}

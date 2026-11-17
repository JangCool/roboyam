package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 모집({@code trials}, V241) */
public interface TrialRepository extends IrobitRepository<TrialEntity, Long> {

    /** 고치기·뽑기·상태 바꾸기 — 모집 행을 잠가 같은 모집을 동시에 처리하지 못하게 한다(신청 넣기는 이 행을 FK 로 가리키지만 신청 쪽도 늘 이 행을 먼저 잠근다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialEntity> findWithLockById(Long id);

    Optional<TrialEntity> findByBizApplicationId(Long bizApplicationId);
}

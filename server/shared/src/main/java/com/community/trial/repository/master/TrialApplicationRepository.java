package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 신청({@code trial_applications}, V241) */
public interface TrialApplicationRepository extends IrobitRepository<TrialApplicationEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialApplicationEntity> findWithLockById(Long id);

    List<TrialApplicationEntity> findByTrialIdOrderByCreatedAtAscIdAsc(Long trialId);

    Optional<TrialApplicationEntity> findFirstByTrialIdAndUserIdAndStatusNot(Long trialId, Long userId, TrialApplicationStatus status);

    List<TrialApplicationEntity> findTop100ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByTrialIdAndStatusNot(Long trialId, TrialApplicationStatus status);
}

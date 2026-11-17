package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 뽑힌 줄({@code trial_participants}, V241) */
public interface TrialParticipantRepository extends IrobitRepository<TrialParticipantEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialParticipantEntity> findWithLockById(Long id);

    List<TrialParticipantEntity> findByTrialIdOrderByIdAsc(Long trialId);

    List<TrialParticipantEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TrialParticipantEntity> findByApplicationId(Long applicationId);

    long countByUserIdAndKeptTrue(Long userId);

    long countByUserIdAndReviewLateAtIsNotNull(Long userId);
}

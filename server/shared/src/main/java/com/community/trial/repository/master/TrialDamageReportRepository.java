package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 파손 신고({@code trial_damage_reports}, V241) */
public interface TrialDamageReportRepository extends IrobitRepository<TrialDamageReportEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialDamageReportEntity> findWithLockById(Long id);

    List<TrialDamageReportEntity> findByParticipantIdOrderByCreatedAtDesc(Long participantId);

    List<TrialDamageReportEntity> findByTrialIdOrderByCreatedAtDesc(Long trialId);

    List<TrialDamageReportEntity> findTop500ByStatusAndObjectionDueAtBefore(TrialDamageStatus status, Instant before);
}

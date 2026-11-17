package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialPhotoEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 사진({@code trial_photos}, V241) */
public interface TrialPhotoRepository extends IrobitRepository<TrialPhotoEntity, Long> {

    List<TrialPhotoEntity> findByIdIn(Collection<Long> ids);

    List<TrialPhotoEntity> findByTrialIdAndAttachedAtIsNotNullOrderByIdAsc(Long trialId);

    List<TrialPhotoEntity> findTop200ByAttachedAtIsNullAndCreatedAtBefore(Instant before);
}

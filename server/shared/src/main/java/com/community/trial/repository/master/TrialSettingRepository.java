package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 체험단 설정({@code trial_settings}, V241 — 행 하나) */
public interface TrialSettingRepository extends IrobitRepository<TrialSettingEntity, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialSettingEntity> findWithLockById(Integer id);
}

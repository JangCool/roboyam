package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialStockReceiptEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 운영자 입고({@code trial_stock_receipts}, V241) */
public interface TrialStockReceiptRepository extends IrobitRepository<TrialStockReceiptEntity, Long> {

    Optional<TrialStockReceiptEntity> findByTrialId(Long trialId);
}

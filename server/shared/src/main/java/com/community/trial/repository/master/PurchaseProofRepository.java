package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.PurchaseProofEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 구매 인증({@code purchase_proofs}, V241) */
public interface PurchaseProofRepository extends IrobitRepository<PurchaseProofEntity, Long> {

    /** 확인 — 인증 행을 잠가 두 확인자가 같은 인증을 동시에 처리하지 못하게 한다(이 행을 FK 로 가리키는 표가 없다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PurchaseProofEntity> findWithLockById(Long id);

    /** 그 글의 마지막 인증 */
    Optional<PurchaseProofEntity> findFirstByPostIdOrderByCreatedAtDescIdDesc(Long postId);

    long countByUserIdAndStatus(Long userId, PurchaseProofStatus status);

    List<PurchaseProofEntity> findTop200ByStatusNotAndPurgedAtIsNullAndReviewedAtBefore(PurchaseProofStatus status, Instant before);
}

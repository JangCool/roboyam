package com.community.trial.repository.master;

import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialDamageStatus;
import com.community.trial.entity.TrialSponsorReviewEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 협찬 숨김 신고 확인({@code trial_sponsor_reviews}, V241 — 글마다 한 줄) */
public interface TrialSponsorReviewRepository extends IrobitRepository<TrialSponsorReviewEntity, Long> {

    /** 신고·확인 — 글의 줄을 잠근다(이 행을 FK 로 가리키는 표가 없다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrialSponsorReviewEntity> findWithLockByPostId(Long postId);
}

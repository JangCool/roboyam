package com.community.trial.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 모집 ↔ 광고 신청 건 이력 — {@code trial_biz_applications}(V244). 모집의 지금 결제 건은 {@code trials.biz_application_id} 이고, 선결제 「결제 다시 요청」이 새 건을 만들면 바뀐다.
 * 예전 건도 어느 모집의 건인지 찾을 수 있게 모든 건을 한 줄씩 남긴다(지우지 않는다).
 */
@Entity
@Table(name = "trial_biz_applications")
public class TrialBizApplicationEntity {

    @Id
    private Long bizApplicationId;
    private Long trialId;
    private Instant createdAt;

    protected TrialBizApplicationEntity() {
    }

    public static TrialBizApplicationEntity of(long bizApplicationId, long trialId, Instant now) {
        TrialBizApplicationEntity e = new TrialBizApplicationEntity();
        e.bizApplicationId = bizApplicationId;
        e.trialId = trialId;
        e.createdAt = now;
        return e;
    }

    public Long getBizApplicationId() {
        return bizApplicationId;
    }

    public Long getTrialId() {
        return trialId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

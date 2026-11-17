package com.community.trial.repository.master;

import com.community.trial.entity.TrialBizApplicationEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 모집 ↔ 광고 신청 건 이력({@code trial_biz_applications}, V244) */
public interface TrialBizApplicationRepository extends IrobitRepository<TrialBizApplicationEntity, Long> {
}

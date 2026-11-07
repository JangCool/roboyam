package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** RewardEventLineResult 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventLineResultRepository extends RoboyamRepository<RewardEventLineResultEntity, Long> {
}

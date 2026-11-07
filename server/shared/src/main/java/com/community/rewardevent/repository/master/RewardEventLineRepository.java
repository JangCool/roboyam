package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventLineEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** RewardEventLine 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventLineRepository extends RoboyamRepository<RewardEventLineEntity, Long> {
}

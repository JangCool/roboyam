package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventRunEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** RewardEventRun 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventRunRepository extends RoboyamRepository<RewardEventRunEntity, Long> {
}

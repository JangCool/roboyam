package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** RewardEvent 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventRepository extends RoboyamRepository<RewardEventEntity, Long> {
}

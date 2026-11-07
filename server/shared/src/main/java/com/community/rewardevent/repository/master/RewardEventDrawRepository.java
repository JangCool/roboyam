package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventDrawEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** RewardEventDraw 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventDrawRepository extends RoboyamRepository<RewardEventDrawEntity, Long> {
}

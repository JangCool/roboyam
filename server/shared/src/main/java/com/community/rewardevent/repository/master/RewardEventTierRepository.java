package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventTierEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** RewardEventTier 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventTierRepository extends IrobitRepository<RewardEventTierEntity, Long> {
}

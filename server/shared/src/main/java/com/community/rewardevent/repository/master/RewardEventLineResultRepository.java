package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventLineResultEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** RewardEventLineResult 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventLineResultRepository extends IrobitRepository<RewardEventLineResultEntity, Long> {
}

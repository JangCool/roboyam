package com.community.rewardevent.repository.master;

import com.community.rewardevent.entity.RewardEventRecipientEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** RewardEventRecipient 넣기(persist) — 읽기·조건부 고치기는 {@link RewardEventStore} */
public interface RewardEventRecipientRepository extends RoboyamRepository<RewardEventRecipientEntity, Long> {
}

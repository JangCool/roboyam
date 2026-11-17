package com.community.trial.repository.master;

import com.community.trial.entity.TrialAddressViewEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 운영자 주소 열람 기록({@code trial_address_views}, V243) */
public interface TrialAddressViewRepository extends IrobitRepository<TrialAddressViewEntity, Long> {

    long countByParticipantId(Long participantId);
}

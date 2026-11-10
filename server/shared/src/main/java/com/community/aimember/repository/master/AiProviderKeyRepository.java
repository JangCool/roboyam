package com.community.aimember.repository.master;

import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 제공사 API 키(암호문) */
public interface AiProviderKeyRepository extends RoboyamRepository<AiProviderKeyEntity, AiProvider> {
}

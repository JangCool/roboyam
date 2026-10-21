package com.community.auth.repository.master;

import com.community.auth.entity.AuthTokenEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 시각 조건이 붙는 질의는 {@link AuthTokenRepositoryQDSL} 에 있다 */
public interface AuthTokenRepository extends RoboyamRepository<AuthTokenEntity, Long> {
}

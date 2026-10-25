package com.community.auth.repository.master;

import com.community.auth.entity.AuthTokenEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

/** 시각 조건이 붙는 질의는 {@link AuthTokenRepositoryQDSL} 에 있다 */
public interface AuthTokenRepository extends RoboyamRepository<AuthTokenEntity, Long> {

    /** 토큰 해시(원문의 SHA-256 16진 64자)로 찾는다 — 유일 색인 {@code ux_auth_tokens_hash} 를 탄다 */
    Optional<AuthTokenEntity> findByTokenHash(String tokenHash);
}

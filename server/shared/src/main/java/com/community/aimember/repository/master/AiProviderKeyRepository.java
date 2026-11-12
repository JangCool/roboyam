package com.community.aimember.repository.master;

import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 제공사 API 키(암호문) */
public interface AiProviderKeyRepository extends IrobitRepository<AiProviderKeyEntity, AiProvider> {

    /** 상태 바꾸기 — 키 저장과 실행기의 쉬게 하기가 서로 줄 선다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiProviderKeyEntity> findWithLockByProvider(AiProvider provider);
}

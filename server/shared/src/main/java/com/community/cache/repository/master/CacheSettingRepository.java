package com.community.cache.repository.master;

import com.community.cache.entity.CacheSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 캐시별 설정({@code cache_settings}) — 바꿀 때는 쓰기 잠금으로 읽는다(세대 번호를 동시에 올리는 저장끼리 줄 세운다). 주기 읽기는 {@link CacheStateStore} 프로젝션 */
public interface CacheSettingRepository extends IrobitRepository<CacheSettingEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CacheSettingEntity> findWithLockByCacheKey(String cacheKey);
}

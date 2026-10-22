package com.community.social.repository.master;

import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface SocialProviderSettingRepository extends RoboyamRepository<SocialProviderSettingEntity, SocialProvider> {

    /** 켜 둔 공급자의 설정(최대 3건) */
    List<SocialProviderSettingEntity> findByEnabledTrue();

    /** 행을 쓰기 잠금으로 읽는다 — 관리자 설정 저장·삭제를 공급자마다 한 번에 하나씩 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SocialProviderSettingEntity> findWithLockByProvider(SocialProvider provider);
}

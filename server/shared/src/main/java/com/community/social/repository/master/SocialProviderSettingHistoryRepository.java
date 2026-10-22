package com.community.social.repository.master;

import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingHistoryEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SocialProviderSettingHistoryRepository extends RoboyamRepository<SocialProviderSettingHistoryEntity, Long> {

    /** 한 공급자의 이력 한 쪽 — 정렬은 {@code pageable} 이 정한다 */
    Page<SocialProviderSettingHistoryEntity> findByProvider(SocialProvider provider, Pageable pageable);
}

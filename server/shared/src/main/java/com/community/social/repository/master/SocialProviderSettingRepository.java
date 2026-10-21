package com.community.social.repository.master;

import com.community.social.entity.SocialProvider;
import com.community.social.entity.SocialProviderSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface SocialProviderSettingRepository extends RoboyamRepository<SocialProviderSettingEntity, SocialProvider> {

    /** 켜 둔 공급자의 설정(최대 3건) */
    List<SocialProviderSettingEntity> findByEnabledTrue();
}

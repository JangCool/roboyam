package com.community.cache.repository.master;

import com.community.cache.entity.CacheSettingChangeEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 설정 변경 이력({@code cache_setting_changes}) — 넣기는 {@code persist}. 읽기는 {@link CacheStateStore} */
public interface CacheSettingChangeRepository extends RoboyamRepository<CacheSettingChangeEntity, Long> {
}

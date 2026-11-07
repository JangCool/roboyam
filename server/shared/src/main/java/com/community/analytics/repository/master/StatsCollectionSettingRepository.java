package com.community.analytics.repository.master;

import com.community.analytics.entity.StatsCollectionSettingEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 통계 수집 설정 행({@code stats_collection_settings}) — 질의는 {@code StatsCollectionStore} */
public interface StatsCollectionSettingRepository extends RoboyamRepository<StatsCollectionSettingEntity, Short> {
}

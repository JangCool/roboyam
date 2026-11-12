package com.community.analytics.repository.master;

import com.community.analytics.entity.StatsCollectionSettingEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 통계 수집 설정 행({@code stats_collection_settings}) — 질의는 {@code StatsCollectionStore} */
public interface StatsCollectionSettingRepository extends IrobitRepository<StatsCollectionSettingEntity, Short> {
}

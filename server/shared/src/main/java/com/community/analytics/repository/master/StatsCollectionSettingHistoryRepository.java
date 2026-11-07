package com.community.analytics.repository.master;

import com.community.analytics.entity.StatsCollectionSettingHistoryEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 통계 수집 설정 변경 이력({@code stats_collection_setting_history}) — 넣기는 {@code persist}. 질의는 {@code StatsCollectionStore} */
public interface StatsCollectionSettingHistoryRepository extends RoboyamRepository<StatsCollectionSettingHistoryEntity, Long> {
}

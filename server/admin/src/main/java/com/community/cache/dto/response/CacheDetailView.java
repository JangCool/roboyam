package com.community.cache.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * 캐시 상세(서랍) — 목록 한 줄(같은 칸을 펼쳐 싣는다) + 서버별 상태 + 최근 기록. 모양은 admin-web {@code CacheDetailView}({@code CacheView} 를 넓힌 것).
 */
public record CacheDetailView(@JsonUnwrapped CacheView cache, List<CacheNodeStateView> nodes, List<CacheRefreshLogView> refreshes,
                              List<CacheSettingChangeView> settingChanges, List<CacheGadgetPlacementView> gadgetPlacements, Integer distinctConfigs) {
}

package com.community.cache.repository.master;

import com.community.cache.entity.CacheRefreshLogEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 갱신 기록({@code cache_refresh_logs}) — 넣기는 {@code persist}. 읽기는 {@link CacheStateStore} */
public interface CacheRefreshLogRepository extends IrobitRepository<CacheRefreshLogEntity, Long> {
}

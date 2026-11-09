package com.community.cache.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 캐시 목록 한 줄 — 여러 서버 값은 합친다(항목 수·적중·누락은 합, 마지막 갱신은 가장 늦은 서버). 모양은 admin-web {@code useCacheAdminApi.types.ts} {@code CacheView}.
 */
public record CacheView(String cacheKey, String displayName, String target, boolean targetIsPath, String kind, List<String> servers, int ttlSeconds,
                        int defaultTtlSeconds, int minTtlSeconds, int maxTtlSeconds, String memberMode, long entryCount, long hitCount, long missCount,
                        long generation, Instant lastRefreshedAt, String status, boolean enabled, boolean effectiveEnabled, List<String> pendingNodeIds,
                        Instant pendingSince, String gadgetKind, List<CacheUsageView> usages, List<CacheAdminLinkView> adminLinks) {
}

package com.community.cache.dto.response;

import java.time.Instant;

/** 서랍의 서버별 상태 한 줄 */
public record CacheNodeStateView(String nodeId, String role, long appliedGeneration, boolean appliedEnabled, Instant lastClearedAt, Instant lastCheckedAt,
                                 long entryCount, long hitCount, long missCount) {
}

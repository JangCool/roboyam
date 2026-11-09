package com.community.cache.dto.request;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /api/admin/caches/settings} · {@code PUT /api/admin/caches/{cacheKey}/enabled} 본문 */
public record CacheEnabledRequest(@NotNull(message = "{cache.enabled.required}") Boolean enabled) {
}

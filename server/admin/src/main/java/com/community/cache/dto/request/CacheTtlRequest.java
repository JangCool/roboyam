package com.community.cache.dto.request;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /api/admin/caches/{cacheKey}/ttl} 본문 — 범위는 캐시 분류마다 달라 서비스가 검사한다(400 {@code fields.ttlSeconds}) */
public record CacheTtlRequest(@NotNull(message = "{cache.ttl.required}") Integer ttlSeconds) {
}

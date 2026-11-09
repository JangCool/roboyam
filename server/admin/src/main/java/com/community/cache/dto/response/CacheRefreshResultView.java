package com.community.cache.dto.response;

import java.time.Instant;
import java.util.List;

/** 「지금 갱신」·「전체 갱신」 응답(202) — 요청 시각과 바뀐 목록 줄(전체 갱신이면 전부) */
public record CacheRefreshResultView(Instant requestedAt, List<CacheView> caches) {
}

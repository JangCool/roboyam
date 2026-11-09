package com.community.cache.dto.response;

import java.time.Instant;

/** 갱신 기록 한 줄 — 계기 MANUAL · AUTO_SAVE, 자동일 때 무엇을 저장해서인지(요청 언어, 수동이면 null) */
public record CacheRefreshLogView(String id, Instant requestedAt, String trigger, boolean all, String source, String operatorName, long generation) {
}

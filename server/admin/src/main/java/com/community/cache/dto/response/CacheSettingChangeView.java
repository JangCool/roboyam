package com.community.cache.dto.response;

import java.time.Instant;

/** 설정 변경 이력 한 줄 — 종류 TTL · ENABLED · GLOBAL_ENABLED */
public record CacheSettingChangeView(String id, Instant changedAt, String kind, Integer fromSeconds, Integer toSeconds, Boolean enabled, String operatorName) {
}

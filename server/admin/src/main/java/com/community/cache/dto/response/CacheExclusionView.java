package com.community.cache.dto.response;

import java.util.List;

/** 캐시하지 않는 가젯 종류 — 이유(요청 언어) · 함께 담기는 캐시(없으면 빈 배열) */
public record CacheExclusionView(String gadgetKind, String displayName, String reason, List<String> includedIn) {
}

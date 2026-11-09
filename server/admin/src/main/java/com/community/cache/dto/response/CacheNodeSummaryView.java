package com.community.cache.dto.response;

import java.time.Instant;

/** 서버 한 대의 요약 — 이름(노드 ID) · 종류(API · ADMIN) · 마지막 확인 시각 */
public record CacheNodeSummaryView(String nodeId, String role, Instant lastCheckedAt) {
}

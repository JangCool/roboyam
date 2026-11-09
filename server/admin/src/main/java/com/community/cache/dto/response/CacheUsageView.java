package com.community.cache.dto.response;

/** 쓰이는 곳 한 줄 — 종류 AREA · ACTION · SERVER, 이름·설명은 요청 언어 */
public record CacheUsageView(String kind, String label, String description, String path, CacheUsageSampleView sample, boolean loginRequired) {
}

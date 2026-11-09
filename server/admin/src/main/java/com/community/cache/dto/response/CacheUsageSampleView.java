package com.community.cache.dto.response;

/** 예시 경로 규칙 — 종류 · 경로 틀({@code {id}}) · 서버가 고른 예시 번호(없으면 null) */
public record CacheUsageSampleView(String kind, String template, String sampleId) {
}

package com.community.cache.definition;

/** 쓰이는 곳의 종류 — 화면 영역(값이 그대로 보인다) · 동작(누르거나 쓸 때 규칙이 적용된다) · 서버 내부(화면 없이 서버에서만) */
public enum CacheUsageKind {
    AREA, ACTION, SERVER
}

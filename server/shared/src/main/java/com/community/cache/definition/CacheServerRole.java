package com.community.cache.definition;

import java.util.Locale;

/** 캐시를 두는 서버 종류 — 공개 사이트 api 서버(여러 대) · 관리자 서버. 앱의 {@code irobit.schema.stream}(api · admin)으로 자기 종류를 안다 */
public enum CacheServerRole {
    API, ADMIN;

    /** 스키마 스트림 이름(api · admin) → 종류. 모르는 이름이면 API */
    public static CacheServerRole ofStream(String stream) {
        return "admin".equalsIgnoreCase(stream == null ? "" : stream.strip()) ? ADMIN : API;
    }

    public String lower() {
        return name().toLowerCase(Locale.ROOT);
    }
}

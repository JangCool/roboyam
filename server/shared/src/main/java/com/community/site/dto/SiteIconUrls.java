package com.community.site.dto;

/** 올려 둔 사이트 아이콘의 주소들(저장소 공개 주소 — 로컬은 {@code /media/…} 상대 주소) */
public record SiteIconUrls(String version, String favicon32, String favicon48, String appleTouch180, String icon192, String icon512) {
}

package com.community.cache.dto.response;

/** 적용 영역 미리보기에서 고를 수 있는 쪽 — 공개 사이트 경로 · 이름(요청 언어, 게시판·가젯 페이지 이름 포함) */
public record CachePreviewPageView(String path, String label) {
}
